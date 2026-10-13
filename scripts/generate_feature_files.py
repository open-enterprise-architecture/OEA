#!/usr/bin/env python3
"""
Generates Gherkin feature files from OEA requirement files.
V-model: the system test specification is created in parallel to the requirements phase.

Usage: python3 scripts/generate_feature_files.py
"""

import os
import re
from pathlib import Path

REQ_DIR = Path("requirements/req")
OUT_DIR = Path("requirements/tests")

# Bootstrap REQs do not need a login as precondition
BOOTSTRAP_REQS = {
    "REQ-013", "REQ-014", "REQ-015", "REQ-016",
    "REQ-017", "REQ-018", "REQ-019",
}
# Login REQs describe the login itself
LOGIN_REQS = {
    "REQ-001", "REQ-002", "REQ-003", "REQ-004", "REQ-005",
    "REQ-006", "REQ-007", "REQ-008", "REQ-009", "REQ-010",
    "REQ-011", "REQ-012",
}

TYPE_TAGS = {
    "functional":     "@functional",
    "non-functional": "@non-functional @performance",
    "compliance":     "@compliance",
    "business-rule":  "@business-rule",
    "constraint":     "@constraint",
    "interface":      "@interface",
    "data":           "@data",
}


# ---------------------------------------------------------------------------
# Frontmatter parser (without an external YAML library)
# ---------------------------------------------------------------------------

def parse_frontmatter(content: str) -> tuple:
    """Reads YAML frontmatter and returns (meta_dict, body)."""
    meta = {}
    if not content.startswith("---"):
        return meta, content
    parts = content.split("---", 2)
    if len(parts) < 3:
        return meta, content
    fm = parts[1]
    body = parts[2]

    # Simple line parser (sufficient for the REQ fields)
    for line in fm.splitlines():
        line = line.rstrip()
        if not line or line.startswith("#"):
            continue
        if ":" in line and not line.startswith(" ") and not line.startswith("-"):
            k, _, v = line.partition(":")
            v = v.strip().strip('"').strip("'")
            meta[k.strip()] = v if v else None
        # use_cases / UC references as a list
        if line.strip().startswith("- UC-"):
            uc = line.strip().lstrip("- ").strip()
            meta.setdefault("_uc_refs", []).append(uc)
        if line.strip().startswith("- ADR-"):
            adr = line.strip().lstrip("- ").strip()
            meta.setdefault("_adr_refs", []).append(adr)

    return meta, body


# ---------------------------------------------------------------------------
# Extract Markdown section
# ---------------------------------------------------------------------------

def extract_section(body: str, *headings: str) -> str:
    """Return the first matching section; several headings act as fallbacks
    (English heading first, legacy German heading second)."""
    for heading in headings:
        pattern = rf"##\s+{re.escape(heading)}\s*\n(.*?)(?=\n##\s|\Z)"
        m = re.search(pattern, body, re.DOTALL)
        if m:
            return m.group(1).strip()
    return ""


# ---------------------------------------------------------------------------
# Parse acceptance criteria
# ---------------------------------------------------------------------------

def parse_acs(ac_text: str) -> list:
    """Splits the AC block into structured scenarios."""
    acs = []
    blocks = re.split(r'\n(?=\*\*AC\d+)', ac_text)
    for block in blocks:
        block = block.strip()
        if not block:
            continue
        header = re.match(r'\*\*AC(\d+)\*\*\s*(?:\(([^)]*)\))?[:\s]*\n?(.*)',
                          block, re.DOTALL)
        if not header:
            continue
        num    = header.group(1)
        label  = (header.group(2) or "").strip()
        body   = header.group(3)

        given, when, then, other = [], [], [], []
        for raw_line in body.splitlines():
            line = raw_line.strip()
            # English keywords first, legacy German keywords as fallback
            if re.match(r'^- (?:Given|Gegeben)[: ]', line):
                given.append(re.sub(r'^- (?:Given|Gegeben)[: ]+', '', line).strip())
            elif re.match(r'^- (?:When|Wenn)[: ]', line):
                when.append(re.sub(r'^- (?:When|Wenn)[: ]+', '', line).strip())
            elif re.match(r'^- (?:Then|Dann)[: ]', line):
                then.append(re.sub(r'^- (?:Then|Dann)[: ]+', '', line).strip())
            elif line and not line.startswith("#"):
                other.append(line)

        acs.append({
            "num": num, "label": label,
            "given": given, "when": when, "then": then,
            "has_structure": bool(given or when or then),
            "raw": body.strip(),
        })
    return acs


# ---------------------------------------------------------------------------
# Build feature file
# ---------------------------------------------------------------------------

def scenario_name(ac: dict) -> str:
    """Short, descriptive scenario name derived from the AC content."""
    candidates = ac["given"] + ac["when"]
    if candidates:
        name = candidates[0]
    elif ac["raw"]:
        name = ac["raw"].splitlines()[0].lstrip("- ").strip()
    else:
        name = f"AC{ac['num']}"
    return (name[:72] + "...") if len(name) > 75 else name


def wrap(text: str, indent: str = "  ", width: int = 72) -> list:
    """Wraps long text into several indented lines."""
    words = text.split()
    lines, cur = [], indent
    for w in words:
        if len(cur) + len(w) + 1 > width:
            lines.append(cur.rstrip())
            cur = indent + w + " "
        else:
            cur += w + " "
    if cur.strip():
        lines.append(cur.rstrip())
    return lines


def build_background(req_id: str, req_type: str, uc_refs: list) -> list:
    lines = []
    if req_type != "functional":
        return lines   # NFR / compliance have their own context
    lines.append("  Background:")
    if req_id in BOOTSTRAP_REQS:
        lines.append("    Given a freshly installed OEA instance without a system admin account")
    elif req_id in LOGIN_REQS:
        lines.append("    Given an OEA instance with a configured authentication mechanism")
        lines.append("    And a person with email \"test@example.com\" and an active role exists")
    else:
        lines.append("    Given a running OEA instance")
        lines.append("    And a user with the required role is logged in")
    lines.append("")
    return lines


def build_feature(meta: dict, body: str) -> str:
    req_id   = meta.get("id", "REQ-???")
    title    = meta.get("title", "Unknown")
    req_type = meta.get("type", "functional")
    priority = meta.get("priority", "must")
    status   = meta.get("status", "proposed")
    uc_refs  = meta.get("_uc_refs", [])

    statement  = extract_section(body, "Statement", "Aussage")
    ac_text    = extract_section(body, "Acceptance Criteria", "Akzeptanzkriterien")
    verif      = extract_section(body, "Verification Method", "Verifikationsmethode")
    acs       = parse_acs(ac_text)

    # Tags
    type_tag = TYPE_TAGS.get(req_type, "@functional")
    tags = [type_tag, f"@{priority}"]
    if status == "rejected":
        tags.append("@skip")
    if uc_refs:
        tags += [f"@{uc}" for uc in uc_refs]

    # Verification hint
    verif_hint = ""
    for vl in verif.splitlines():
        vl = vl.strip().lstrip("- [x] ").lstrip("- [ ] ").strip()
        if vl.startswith(("Method:", "Methode:")):
            verif_hint = vl
            break

    out = []
    out.append(f"# Derived from: requirements/req/{req_id.lower().replace('-', '-', 1)}-*.md")
    out.append("# V-model system test specification – created 2026-06-30")
    if verif_hint:
        out.append(f"# Verification: {verif_hint}")
    out.append("")
    out.append(" ".join(tags))
    out.append(f"Feature: {req_id} – {title}")
    out.append("")

    # Short description (first 2 sentences of the statement)
    if statement:
        sentences = re.split(r'(?<=[.!?])\s+', statement)
        short = " ".join(sentences[:2])
        out += wrap(short, "  ")
        out.append("")

    # Background
    out += build_background(req_id, req_type, uc_refs)

    # Scenarios
    if acs:
        for ac in acs:
            lbl = f" – {ac['label']}" if ac['label'] else ""
            out.append(f"  @AC{ac['num']}")
            out.append(f"  Scenario: AC{ac['num']}{lbl} – {scenario_name(ac)}")

            if ac["has_structure"]:
                first = True
                for g in ac["given"]:
                    out.append(f"    {'Given' if first else 'And  '} {g}")
                    first = False
                if not ac["given"]:
                    out.append("    Given the precondition according to the requirement is met")

                first = True
                for w in ac["when"]:
                    out.append(f"    {'When ' if first else 'And  '} {w}")
                    first = False
                if not ac["when"]:
                    out.append("    When the described action is performed")

                first = True
                for t in ac["then"]:
                    out.append(f"    {'Then ' if first else 'And  '} {t}")
                    first = False
                if not ac["then"]:
                    out.append("    Then the expected result has occurred")
            else:
                # No structured Given/When/Then – raw text as comment
                out.append("    # TODO: AC without standard structure – refine manually")
                for raw_line in ac["raw"].splitlines()[:6]:
                    if raw_line.strip():
                        out.append(f"    # {raw_line.strip()}")
                out.append("    Given the precondition according to the requirement is met")
                out.append("    When the described action is performed")
                out.append("    Then the system behaves according to the statement")

            out.append("")
    else:
        out.append("  # TODO: No acceptance criteria found – add manually")
        out.append("  Scenario: Requirement is fulfilled")
        out.append("    Given the preconditions according to the requirement document are met")
        out.append("    When the described action is performed")
        out.append("    Then the system behaves according to the statement")
        out.append("")

    return "\n".join(out)


# ---------------------------------------------------------------------------
# Main routine
# ---------------------------------------------------------------------------

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    req_files = sorted(REQ_DIR.glob("REQ-*.md"))
    print(f"Processing {len(req_files)} requirement files ...\n")

    ok = skip = warn = 0
    for req_file in req_files:
        content = req_file.read_text(encoding="utf-8")
        meta, body = parse_frontmatter(content)

        if not meta.get("id"):
            print(f"  SKIP (no id):  {req_file.name}")
            skip += 1
            continue

        try:
            feature = build_feature(meta, body)
            out_path = OUT_DIR / req_file.name.replace(".md", ".feature")
            out_path.write_text(feature, encoding="utf-8")

            acs = parse_acs(extract_section(body, "Acceptance Criteria", "Akzeptanzkriterien"))
            structured = sum(1 for a in acs if a["has_structure"])
            flag = "⚠" if structured < len(acs) else "✓"
            print(f"  {flag} {out_path.name}  ({len(acs)} ACs, {structured} structured)")
            if structured < len(acs):
                warn += 1
            ok += 1
        except Exception as e:
            print(f"  ✗ ERROR {req_file.name}: {e}")
            skip += 1

    print(f"\n{'='*60}")
    print(f"  Created:  {ok} feature files")
    print(f"  Warnings: {warn} (ACs without standard structure, TODO marker)")
    print(f"  Skipped: {skip}")
    print(f"  Output:   {OUT_DIR}/")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
