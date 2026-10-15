#!/usr/bin/env python3
"""Check the authorization declarations of api/openapi.yaml (ADR-032, REQ-158).

Fails (exit code 1) if
- an operation lacks the vendor extension ``x-oea-authorization``,
- the extension is malformed (unknown ``access`` value, missing or empty permission
  list, unknown keys),
- an operation references a permission that is not in the catalog
  ``x-oea-permissions`` at the top level of the spec,
- ``access: public`` is used outside the allowlist of ADR-032 §8 (layer L1),
- an operation that is not public lacks a ``401`` or ``403`` response, or disables
  authentication with ``security: []``,
- the catalog or the built-in roles (``x-oea-builtin-roles``) are inconsistent
  (unknown permissions, SoD-2, SoD-4).

The catalog is read from the spec itself, so it is kept in one place. Both
extensions and every ``x-oea-authorization`` value are written as JSON (valid YAML
flow style), which allows parsing without a YAML library: the script uses only the
Python standard library and a line-based scan of the ``paths`` section.

Usage: python3 check_openapi_authorization.py [path/to/openapi.yaml]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# Operations allowed to be called without an authenticated principal (ADR-032 §8, L1).
# Extending this list is a security decision and needs a review.
PUBLIC_ALLOWLIST = {
    "issueClientCredentialsToken",
    "getClientCompatibility",  # REQ-195: version check before login, no instance data
    "getHealth",  # REQ-075: health probe for orchestrators, status only
}

ACCESS_VALUES = {"public", "authenticated", "permission"}
ALLOWED_KEYS = {"access", "permissions", "anyOfPermissions", "conditions"}
HTTP_METHODS = {"get", "put", "post", "delete", "patch", "head", "options", "trace"}
SOD2_METAMODEL = {"metamodel:write"}
SOD2_VISIBILITY = {"visibility:configure", "visibility-scope:assign"}
PERMISSION_RE = re.compile(r"^[a-z][a-z-]*:[a-z][a-z-]*$")

PATH_RE = re.compile(r"^  (/\S*):\s*(#.*)?$")
METHOD_RE = re.compile(r"^    ([a-z]+):\s*(#.*)?$")
OPERATION_ID_RE = re.compile(r"^      operationId:\s*['\"]?([^'\"\s]+)")
EXTENSION_RE = re.compile(r"^      x-oea-authorization:(.*)$")
RESPONSES_RE = re.compile(r"^      responses:\s*$")
RESPONSE_CODE_RE = re.compile(r"^        ['\"]?(\d{3}|default)['\"]?:")
EMPTY_SECURITY_RE = re.compile(r"^      security:\s*\[\s*\]\s*$")


def find_spec(argv: list[str]) -> Path:
    if len(argv) > 1:
        return Path(argv[1])
    for base in [Path.cwd(), *Path(__file__).resolve().parents]:
        candidate = base / "api" / "openapi.yaml"
        if candidate.is_file():
            return candidate
    sys.exit("api/openapi.yaml not found; pass the path as argument")


def line_offsets(text: str) -> list[int]:
    offsets, pos = [], 0
    for line in text.split("\n"):
        offsets.append(pos)
        pos += len(line) + 1
    return offsets


def decode_json_at(text: str, start: int, what: str, errors: list[str]):
    """Decode the JSON value starting at the first non-blank character after ``start``."""
    match = re.compile(r"\S").search(text, start)
    if not match or match.group() not in "[{":
        errors.append(f"{what}: value must be written as JSON (flow style)")
        return None
    try:
        value, _ = json.JSONDecoder().raw_decode(text, match.start())
    except json.JSONDecodeError as exc:
        errors.append(f"{what}: invalid JSON ({exc.msg} at line {text.count(chr(10), 0, exc.pos) + 1})")
        return None
    return value


def read_top_level(text: str, key: str, errors: list[str]):
    match = re.search(rf"^{re.escape(key)}:", text, re.MULTILINE)
    if not match:
        errors.append(f"top-level extension '{key}' is missing")
        return None
    return decode_json_at(text, match.end(), key, errors)


def check_catalog(permissions, roles, errors: list[str]) -> dict[str, bool]:
    catalog: dict[str, bool] = {}
    if not isinstance(permissions, list) or not permissions:
        errors.append("x-oea-permissions must be a non-empty list")
        return catalog
    for entry in permissions:
        pid = entry.get("id") if isinstance(entry, dict) else None
        if not isinstance(pid, str) or not PERMISSION_RE.match(pid):
            errors.append(f"x-oea-permissions: invalid entry {entry!r}")
            continue
        if pid in catalog:
            errors.append(f"x-oea-permissions: duplicate permission '{pid}'")
        if not isinstance(entry.get("adminClass"), bool):
            errors.append(f"x-oea-permissions: '{pid}' needs a boolean 'adminClass'")
        catalog[pid] = bool(entry.get("adminClass"))

    if not isinstance(roles, list) or not roles:
        errors.append("x-oea-builtin-roles must be a non-empty list")
        return catalog
    seen: set[str] = set()
    for role in roles:
        rid = role.get("id") if isinstance(role, dict) else None
        if not isinstance(rid, str):
            errors.append(f"x-oea-builtin-roles: invalid entry {role!r}")
            continue
        if rid in seen:
            errors.append(f"x-oea-builtin-roles: duplicate role '{rid}'")
        seen.add(rid)
        perms = set(role.get("permissions", []))
        for unknown in sorted(perms - catalog.keys()):
            errors.append(f"x-oea-builtin-roles: role '{rid}' uses unknown permission '{unknown}'")
        if perms & SOD2_METAMODEL and perms & SOD2_VISIBILITY:
            errors.append(f"x-oea-builtin-roles: role '{rid}' violates SoD-2")
        if role.get("assignableToServiceAccounts") and any(catalog.get(p) for p in perms):
            errors.append(f"x-oea-builtin-roles: role '{rid}' is assignable to service accounts but privileged (SoD-4)")
    return catalog


def check_permission_list(value, label: str, catalog: dict[str, bool], errors: list[str]) -> None:
    if not isinstance(value, list) or not value:
        errors.append(f"{label}: must be a non-empty list")
        return
    for perm in value:
        if perm not in catalog:
            errors.append(f"{label}: unknown permission '{perm}'")


def check_declaration(op: dict, catalog: dict[str, bool], errors: list[str]) -> None:
    label = f"{op['method'].upper()} {op['path']} ({op['operationId'] or 'no operationId'})"
    decl = op["declaration"]
    if decl is None:
        errors.append(f"{label}: missing x-oea-authorization")
        return
    if not isinstance(decl, dict):
        errors.append(f"{label}: x-oea-authorization must be a mapping")
        return
    for key in sorted(set(decl) - ALLOWED_KEYS):
        errors.append(f"{label}: unknown key '{key}' in x-oea-authorization")
    access = decl.get("access")
    if access not in ACCESS_VALUES:
        errors.append(f"{label}: access must be one of {sorted(ACCESS_VALUES)}, got {access!r}")
        return

    if access == "permission":
        has_all = "permissions" in decl
        has_any = "anyOfPermissions" in decl
        if has_all == has_any:
            errors.append(f"{label}: exactly one of 'permissions' or 'anyOfPermissions' is required")
        if has_all:
            check_permission_list(decl["permissions"], f"{label} permissions", catalog, errors)
        if has_any:
            check_permission_list(decl["anyOfPermissions"], f"{label} anyOfPermissions", catalog, errors)
    elif {"permissions", "anyOfPermissions", "conditions"} & decl.keys():
        errors.append(f"{label}: access '{access}' must not list permissions or conditions")

    for i, cond in enumerate(decl.get("conditions", []) or []):
        clabel = f"{label} conditions[{i}]"
        if not isinstance(cond, dict) or not isinstance(cond.get("when"), str) or not cond["when"].strip():
            errors.append(f"{clabel}: needs a non-empty 'when'")
            continue
        check_permission_list(cond.get("permissions"), f"{clabel} permissions", catalog, errors)
    if "conditions" in decl and not isinstance(decl["conditions"], list):
        errors.append(f"{label}: conditions must be a list")

    if access == "public":
        if op["operationId"] not in PUBLIC_ALLOWLIST:
            errors.append(f"{label}: access 'public' is not allowlisted (ADR-032 §8, L1)")
    else:
        for code in ("401", "403"):
            if code not in op["responses"]:
                errors.append(f"{label}: missing '{code}' response")
        if op["emptySecurity"]:
            errors.append(f"{label}: 'security: []' is only allowed for public operations")


def scan_operations(text: str, errors: list[str]) -> list[dict]:
    lines = text.split("\n")
    offsets = line_offsets(text)
    operations: list[dict] = []
    in_paths = False
    path = None
    op = None
    in_responses = False
    for idx, line in enumerate(lines):
        if not in_paths:
            in_paths = line.rstrip() == "paths:"
            continue
        if line and not line[0].isspace() and not line.startswith("#"):
            break  # next top-level key ends the paths section
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent <= 2:
            match = PATH_RE.match(line)
            path, op = (match.group(1) if match else None), None
            continue
        if indent == 4:
            match = METHOD_RE.match(line)
            op = None
            if path and match and match.group(1) in HTTP_METHODS:
                op = {"path": path, "method": match.group(1), "operationId": None,
                      "declaration": None, "responses": set(), "emptySecurity": False}
                operations.append(op)
            in_responses = False
            continue
        if op is None:
            continue
        if indent == 6:
            in_responses = bool(RESPONSES_RE.match(line))
            if m := OPERATION_ID_RE.match(line):
                op["operationId"] = m.group(1)
            elif m := EXTENSION_RE.match(line):
                where = f"{op['method'].upper()} {op['path']} x-oea-authorization"
                op["declaration"] = decode_json_at(text, offsets[idx] + m.start(1), where, errors) or {}
            elif EMPTY_SECURITY_RE.match(line):
                op["emptySecurity"] = True
        elif indent == 8 and in_responses:
            if m := RESPONSE_CODE_RE.match(line):
                op["responses"].add(m.group(1))
    if not in_paths:
        errors.append("no 'paths:' section found")
    return operations


def main(argv: list[str]) -> int:
    spec = find_spec(argv)
    text = spec.read_text(encoding="utf-8")
    errors: list[str] = []

    catalog = check_catalog(read_top_level(text, "x-oea-permissions", errors),
                            read_top_level(text, "x-oea-builtin-roles", errors), errors)
    operations = scan_operations(text, errors)
    if not operations:
        errors.append("no operations found in 'paths'")

    seen_ids: set[str] = set()
    for op in operations:
        if op["operationId"] in seen_ids:
            errors.append(f"duplicate operationId '{op['operationId']}'")
        seen_ids.add(op["operationId"])
        check_declaration(op, catalog, errors)

    if errors:
        print(f"{spec}: {len(errors)} authorization declaration problem(s):")
        for err in errors:
            print(f"  - {err}")
        return 1
    used: set[str] = set()
    for op in operations:
        decl = op["declaration"] if isinstance(op["declaration"], dict) else {}
        for key in ("permissions", "anyOfPermissions"):
            used.update(decl.get(key) or [])
        for cond in decl.get("conditions") or []:
            used.update(cond.get("permissions") or [])
    unused = sorted(set(catalog) - used)
    print(f"{spec}: {len(operations)} operations declare authorization; "
          f"{len(catalog)} permissions in catalog.")
    if unused:
        print(f"  note: catalog permissions not yet used by any operation: {', '.join(unused)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
