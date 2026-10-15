#!/usr/bin/env python3
"""Check that every development secret is in the known-development-secrets list.

ADR-036 §6, REQ-183 AC6: the production artifact contains SHA-256 hashes of all
secret values used in compose.dev.yaml, dev/ and the Keycloak realm file. The
application refuses to start outside the profile 'dev' with any of these values.
This check extracts secret literals from the development files and fails if the
hash of one of them is missing from the list. Secret values are never printed.

Usage:
    python3 scripts/check_dev_secrets.py [--root DIR] [--print-missing-hashes]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

LIST_PATH = Path("backend/src/main/resources/security/known-dev-secrets.sha256")
SOURCES = [Path("compose.dev.yaml"), Path("dev")]
TEXT_SUFFIXES = {".yaml", ".yml", ".env", ".properties", ".json", ".conf", ".txt", ""}

SECRET_KEY = re.compile(r"(?i)(password|passwd|pwd|secret|token|credential|private[_-]?key|api[_-]?key|totp)")
KEY_VALUE = re.compile(r"""^\s*-?\s*["']?(?P<key>[A-Za-z0-9_.\-\[\]]+)["']?\s*[:=]\s*(?P<value>.*?)\s*$""")
DEFAULT_REF = re.compile(r"\$\{[A-Za-z0-9_]+:?-(?P<default>[^}]*)\}")
REFERENCE = re.compile(r"^\$\{[^}]*\}$")
# Keys that match SECRET_KEY but carry no secret (Keycloak mapper switches, OTP metadata)
NOT_SECRET_KEY = re.compile(r"(?i)(\.claim$|^credentialData$)")
NOT_SECRET_VALUE = {"true", "false", "null", ""}


def is_secret(key: str, value: str) -> bool:
    return (bool(SECRET_KEY.search(key)) and not NOT_SECRET_KEY.search(key)
            and value.strip().lower() not in NOT_SECRET_VALUE)


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def load_list(root: Path) -> set[str]:
    path = root / LIST_PATH
    if not path.exists():
        return set()
    return {
        line.strip().lower()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }


def unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return re.sub(r"\s+#.*$", "", value)


def secrets_in_text(text: str):
    """Yield (line, key, value) for key/value lines whose key names a secret."""
    for number, line in enumerate(text.splitlines(), start=1):
        if line.lstrip().startswith("#"):
            continue
        match = KEY_VALUE.match(line)
        if not match or not SECRET_KEY.search(match.group("key")) or NOT_SECRET_KEY.search(match.group("key")):
            continue
        value = unquote(match.group("value"))
        for default in DEFAULT_REF.finditer(value):
            if default.group("default"):
                yield number, match.group("key"), unquote(default.group("default"))
        if (is_secret(match.group("key"), value) and not REFERENCE.match(value)
                and not DEFAULT_REF.search(value) and value not in ("|", ">")):
            yield number, match.group("key"), value


def secrets_in_json(node, path: str = "$"):
    """Yield (json path, key, value) for secret values, including Keycloak credentials."""
    if isinstance(node, dict):
        if node.get("type") in ("password", "otp") and isinstance(node.get("value"), str):
            yield f"{path}.value", "credential", node["value"]
        for key, value in node.items():
            child = f"{path}.{key}"
            if key == "secretData" and isinstance(value, str):
                try:
                    data = json.loads(value)
                except json.JSONDecodeError:
                    data = {}
                if isinstance(data, dict) and isinstance(data.get("value"), str):
                    yield child, "secretData", data["value"]
            elif isinstance(value, str) and is_secret(key, value):
                yield child, key, value
            else:
                yield from secrets_in_json(value, child)
    elif isinstance(node, list):
        for index, item in enumerate(node):
            yield from secrets_in_json(item, f"{path}[{index}]")


def source_files(root: Path):
    for source in SOURCES:
        path = root / source
        if path.is_file():
            yield path
        elif path.is_dir():
            yield from sorted(p for p in path.rglob("*") if p.is_file() and p.suffix in TEXT_SUFFIXES)


def find_secrets(root: Path):
    for path in source_files(root):
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(root)
        if path.suffix == ".json":
            try:
                document = json.loads(text)
            except json.JSONDecodeError as error:
                raise SystemExit(f"{relative}: invalid JSON ({error})")
            for location, key, value in secrets_in_json(document):
                yield f"{relative} {location}", key, value
        else:
            for line, key, value in secrets_in_text(text):
                yield f"{relative}:{line}", key, value


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--print-missing-hashes", action="store_true",
                        help="print the hashes to add to the list (never the values)")
    args = parser.parse_args()

    known = load_list(args.root)
    checked = 0
    missing = []
    for location, key, value in find_secrets(args.root):
        checked += 1
        digest = sha256(value)
        if digest not in known:
            missing.append((location, key, digest))

    if missing:
        print(f"✗ {len(missing)} development secret(s) missing from {LIST_PATH}:")
        for location, key, digest in missing:
            print(f"  {location}: value of '{key}'")
            if args.print_missing_hashes:
                print(f"    {digest}")
        print("Add the SHA-256 hash of each value to the list (use --print-missing-hashes).")
        return 1
    print(f"✓ {checked} development secret(s) checked, all listed in {LIST_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
