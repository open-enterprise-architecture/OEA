#!/usr/bin/env python3
"""Validate all relative markdown links in concept/.

Typically run from the repo root:
  python3 scripts/validate_links.py

Exit code 0 = all links OK
Exit code 1 = at least one link is broken
"""
import re
import sys
from pathlib import Path

# concept/ directory relative to the repo root (script lives in scripts/)
REPO_ROOT = Path(__file__).resolve().parent.parent
CONCEPT_DIR = REPO_ROOT / "concept"

if not CONCEPT_DIR.is_dir():
    print(f"ERROR: {CONCEPT_DIR} not found – run from the repo root.")
    sys.exit(2)

LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")

broken = []
total = 0

for md_file in sorted(CONCEPT_DIR.rglob("*.md")):
    text = md_file.read_text(encoding="utf-8")
    for m in LINK_RE.finditer(text):
        target = m.group(2)
        if target.startswith(("http://", "https://", "#", "mailto:")):
            continue
        if "#" in target:
            target = target.split("#")[0]
        if not target:
            continue
        total += 1
        resolved = (md_file.parent / target).resolve()
        if not resolved.exists():
            broken.append((md_file.relative_to(CONCEPT_DIR), target))

print(f"Checked: {total} relative links in {CONCEPT_DIR.relative_to(REPO_ROOT)}/")

if broken:
    print(f"\nBroken links ({len(broken)}):")
    for f, t in broken:
        print(f"  {f}: {t}")
    sys.exit(1)
else:
    print("✓ All links valid")
    sys.exit(0)
