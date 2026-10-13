#!/usr/bin/env python3
"""Check docs/screens/SCREENS.md against screen stories and images (ADR-031).

Fails if
- a screen ID is listed twice,
- a linked image does not exist,
- a screen with status `prototype` or `implemented` lacks its story or generated images,
- an image in docs/screens/ or a screen story is not referenced by the inventory.

Usage (from the repository root):
  python3 scripts/check_screens.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENS_DIR = ROOT / "docs" / "screens"
INVENTORY = SCREENS_DIR / "SCREENS.md"
STORIES_DIR = ROOT / "frontend" / "src" / "screens"

ROW_RE = re.compile(r"^\|\s*(SCR-\d{3})\s*\|.*?\|\s*`([\w-]+)`\s*\|(.*)\|\s*$")
LINK_RE = re.compile(r"\]\(\./([^)]+)\)")
STATUSES = {"planned", "legacy-mockup", "prototype", "implemented"}


def main():
    problems = []
    screens = {}
    referenced = set()
    for line in INVENTORY.read_text(encoding="utf-8").splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        sid, status, images = m.groups()
        if sid in screens:
            problems.append(f"{sid}: listed twice")
        if status not in STATUSES:
            problems.append(f"{sid}: unknown status `{status}`")
        screens[sid] = status
        for img in LINK_RE.findall(images):
            referenced.add(img)
            if not (SCREENS_DIR / img).exists():
                problems.append(f"{sid}: image {img} does not exist")

    stories = {}
    if STORIES_DIR.is_dir():
        for story in STORIES_DIR.glob("SCR-*.stories.ts"):
            stories[story.name[:7]] = story
    for sid, status in screens.items():
        if status in ("prototype", "implemented"):
            if sid not in stories:
                problems.append(f"{sid}: status `{status}` but no story frontend/src/screens/{sid}-*.stories.ts")
            for mode in ("light", "dark"):
                png = f"{sid}-{mode}.png"
                referenced.add(png)
                if not (SCREENS_DIR / png).exists():
                    problems.append(f"{sid}: generated image {png} missing")
    for sid, story in stories.items():
        if sid not in screens:
            problems.append(f"{story.relative_to(ROOT)}: no entry in SCREENS.md")

    for img in sorted(p.name for p in SCREENS_DIR.iterdir() if p.suffix in (".svg", ".png")):
        if img not in referenced:
            problems.append(f"docs/screens/{img}: not referenced by SCREENS.md")

    if problems:
        print("Screen inventory inconsistent:")
        for p in problems:
            print(f"  ✗ {p}")
        sys.exit(1)
    print(f"✓ Screen inventory consistent ({len(screens)} screens, {len(stories)} stories)")


if __name__ == "__main__":
    main()
