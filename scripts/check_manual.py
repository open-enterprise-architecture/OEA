#!/usr/bin/env python3
"""Check the coverage of the user manual in manual/ (ADR-045 rules 1 to 3, ADR-046, REQ-196 AC1, AC2).

Every manual page declares in its frontmatter which screens and user stories it covers:

    covers:
      screens: [SCR-020]
      stories: [US-049]

Fails if
- an English page has no German page at the same path under manual/de/, or the other way round,
- a German page covers other screens or stories than its English page,
- a chapter index page is missing in a locale,
- a page names an unknown screen or user story,
- a screen with status `implemented` in docs/screens/SCREENS.md or a user story with status
  `in-progress`, `review`, `done` or `realized` is not covered in English or in German.

The generated API reference (api/reference/) is not checked; it is generated from the contract.

Usage (from the repository root):
  python3 scripts/check_manual.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANUAL = ROOT / "manual"
INVENTORY = ROOT / "docs" / "screens" / "SCREENS.md"
STORIES_DIR = ROOT / "requirements" / "user-stories"

LOCALES = {"en": MANUAL, "de": MANUAL / "de"}
CHAPTERS = ["introduction", "setup", "administration", "usage", "api"]
# Every status from in-progress onward, in both status vocabularies (ADR-046)
STORY_STATUSES = {"in-progress", "review", "done", "realized"}
SKIPPED_PARTS = {"node_modules", ".vitepress", "scripts"}

SCREEN_ROW_RE = re.compile(r"^\|\s*(SCR-\d{3})\s*\|.*?\|\s*`([\w-]+)`\s*\|")
# Story files carry the status as "**Status**: <value>" or as frontmatter field "status: <value>"
STORY_STATUS_RE = re.compile(r"^(?:\*\*Status\*\*:|status:)\s*([\w-]+)", re.MULTILINE)
FLOW_LIST_RE = re.compile(r"^\s+(screens|stories):\s*\[(.*)\]\s*$")


def screens():
    result = {}
    for line in INVENTORY.read_text(encoding="utf-8").splitlines():
        m = SCREEN_ROW_RE.match(line)
        if m:
            result[m.group(1)] = m.group(2)
    return result


def stories(problems):
    result = {}
    for path in sorted(STORIES_DIR.glob("US-*.md")):
        story_id = "-".join(path.name.split("-")[:2])
        m = STORY_STATUS_RE.search(path.read_text(encoding="utf-8"))
        if m:
            result[story_id] = m.group(1)
        else:
            problems.append(f"{path.relative_to(ROOT)}: no status (**Status** line or frontmatter field status)")
    return result


def pages(locale_dir, locale):
    """Relative page paths of one locale, without the generated reference and the other locale."""
    result = {}
    for path in sorted(locale_dir.rglob("*.md")):
        rel = path.relative_to(locale_dir)
        if SKIPPED_PARTS.intersection(rel.parts):
            continue
        if locale == "en" and rel.parts[0] == "de":
            continue
        if rel.parts[:2] == ("api", "reference"):
            continue
        if locale == "en" and rel == Path("README.md"):
            continue
        result[rel.as_posix()] = path
    return result


def covers(path, problems):
    """The screens and stories of the frontmatter `covers`; only flow lists are accepted."""
    text = path.read_text(encoding="utf-8")
    result = {"screens": set(), "stories": set()}
    if not text.startswith("---\n"):
        return result
    frontmatter = text[4:].split("\n---", 1)[0].splitlines()
    in_covers = False
    for line in frontmatter:
        if line.startswith("covers:"):
            in_covers = True
            continue
        if in_covers and line.startswith((" ", "\t")):
            m = FLOW_LIST_RE.match(line)
            if not m:
                problems.append(f"{path.relative_to(ROOT)}: `covers` entries must be flow lists, e.g. `stories: [US-001]`")
                continue
            items = [item.strip().strip("'\"") for item in m.group(2).split(",") if item.strip()]
            result[m.group(1)].update(items)
        else:
            in_covers = False
    return result


def main():
    problems = []
    known_screens = screens()
    known_stories = stories(problems)
    required_screens = {sid for sid, status in known_screens.items() if status == "implemented"}
    required_stories = {sid for sid, status in known_stories.items() if status in STORY_STATUSES}

    locale_pages = {locale: pages(directory, locale) for locale, directory in LOCALES.items()}
    for locale, entries in locale_pages.items():
        other = "de" if locale == "en" else "en"
        for rel in sorted(set(entries) - set(locale_pages[other])):
            problems.append(f"{rel}: exists in {locale} but not in {other}")
        for chapter in CHAPTERS:
            if f"{chapter}/index.md" not in entries:
                problems.append(f"{chapter}/index.md: chapter index missing in {locale}")

    covered = {locale: {"screens": set(), "stories": set()} for locale in LOCALES}
    page_covers = {}
    for locale, entries in locale_pages.items():
        for rel, path in entries.items():
            declared = covers(path, problems)
            page_covers[(locale, rel)] = declared
            for kind, known in (("screens", known_screens), ("stories", known_stories)):
                for item in sorted(declared[kind] - set(known)):
                    problems.append(f"{locale}/{rel}: unknown {'screen' if kind == 'screens' else 'user story'} {item}")
                covered[locale][kind].update(declared[kind])

    for rel in sorted(set(locale_pages["en"]) & set(locale_pages["de"])):
        en, de = page_covers[("en", rel)], page_covers[("de", rel)]
        for kind in ("screens", "stories"):
            if en[kind] != de[kind]:
                only_en = ", ".join(sorted(en[kind] - de[kind])) or "–"
                only_de = ", ".join(sorted(de[kind] - en[kind])) or "–"
                problems.append(f"{rel}: German page covers other {kind} (only en: {only_en}; only de: {only_de})")

    for kind, required, inventory in (
        ("screens", required_screens, "implemented screen"),
        ("stories", required_stories, "user story"),
    ):
        for item in sorted(required):
            missing = [locale for locale in LOCALES if item not in covered[locale][kind]]
            if missing:
                detail = f" (status {known_stories[item]})" if kind == "stories" else ""
                problems.append(f"{item}: {inventory}{detail} not covered in {', '.join(missing)}")

    if problems:
        print("User manual coverage incomplete:")
        for problem in problems:
            print(f"  ✗ {problem}")
        sys.exit(1)
    print(
        f"✓ User manual covers {len(required_screens)} implemented screens and {len(required_stories)} "
        f"user stories in en and de ({len(locale_pages['en'])} pages per language)"
    )


if __name__ == "__main__":
    main()
