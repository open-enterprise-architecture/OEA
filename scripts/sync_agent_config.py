#!/usr/bin/env python3
"""Keep the Claude Code configuration in sync with the single sources of truth.

Sources of truth (edit only these):
  AGENTS.md                      -> CLAUDE.md imports it, nothing else
  .opencode/commands/*.md        -> .claude/commands/<name>.md (symlinks)
  .opencode/agents/*.md          -> .claude/agents/<name>.md (generated)
  OpenSpec CLI + openspec/       -> .claude/ and .opencode/ opsx files (via `openspec update`)

Usage:
  python3 scripts/sync_agent_config.py          # write/refresh .claude/
  python3 scripts/sync_agent_config.py --check  # CI: exit 1 on any drift
"""

import argparse
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OC_COMMANDS = ROOT / ".opencode" / "commands"
OC_AGENTS = ROOT / ".opencode" / "agents"
OC_SKILLS = ROOT / ".opencode" / "skills"
CL_COMMANDS = ROOT / ".claude" / "commands"
CL_AGENTS = ROOT / ".claude" / "agents"
CL_SKILLS = ROOT / ".claude" / "skills"
CLAUDE_MD = ROOT / "CLAUDE.md"

CLAUDE_MD_CONTENT = """\
<!-- Single source of truth is AGENTS.md. Do not add content here – edit AGENTS.md instead. -->
@AGENTS.md
"""

# OpenSpec-generated commands are named opsx-* in OpenCode and live in
# .claude/commands/opsx/ for Claude Code; they are managed by `openspec update`.
OPENSPEC_PREFIX = "opsx-"
GENERATED_MARKER = "<!-- GENERATED from {src} by scripts/sync_agent_config.py – do not edit -->"


def parse_frontmatter(text):
    """Return (frontmatter lines, body) of a Markdown file with --- frontmatter."""
    if not text.startswith("---\n"):
        return [], text
    end = text.index("\n---\n", 4)
    return text[4:end].splitlines(), text[end + 5:]


def parse_agent(path):
    """Extract description and permission rules from an OpenCode agent file."""
    lines, body = parse_frontmatter(path.read_text(encoding="utf-8"))
    description = None
    rules, current = [], None
    for line in lines:
        if line.startswith("description:"):
            description = line.split(":", 1)[1].strip()
        m = re.match(r"\s*-?\s*(action|resource|effect):\s*\"?([^\"]*)\"?\s*$", line)
        if m:
            key, value = m.groups()
            if key == "action":
                current = {}
                rules.append(current)
            current[key] = value
    if not description:
        raise ValueError(f"{path}: missing single-line 'description:' in frontmatter")
    return description, rules, body


def render_claude_agent(src):
    description, rules, body = parse_agent(src)
    src_rel = src.relative_to(ROOT).as_posix()
    out = ["---", f"name: {src.stem}", f"description: {description}", "---", "",
           GENERATED_MARKER.format(src=src_rel), ""]
    if rules:
        # Claude Code subagents cannot enforce path-based permissions, so the
        # OpenCode rules are carried over as binding instructions.
        out += ["## Permissions (binding)", "",
                "Enforced technically in OpenCode; follow them strictly here as well.", ""]
        out += [f"- {r.get('effect', '?')} `{r.get('action', '?')}` on `{r.get('resource', '?')}`"
                for r in rules]
        out.append("")
    return "\n".join(out) + "\n" + body.lstrip("\n")


def expected_state():
    """Map each target path to ('symlink', target) or ('file', content)."""
    state = {CLAUDE_MD: ("file", CLAUDE_MD_CONTENT)}
    for src in sorted(OC_COMMANDS.glob("*.md")):
        if not src.name.startswith(OPENSPEC_PREFIX):
            dest = CL_COMMANDS / src.name
            state[dest] = ("symlink", os.path.relpath(src, dest.parent))
    for src in sorted(OC_AGENTS.glob("*.md")):
        state[CL_AGENTS / src.name] = ("file", render_claude_agent(src))
    return state


def managed_existing():
    """Files in .claude/ this script owns (used to detect stale leftovers)."""
    found = []
    if CL_COMMANDS.is_dir():
        found += [p for p in CL_COMMANDS.glob("*.md") if p.is_symlink()]
    if CL_AGENTS.is_dir():
        found += list(CL_AGENTS.glob("*.md"))
    return found


def openspec_versions():
    """Collect generatedBy versions of OpenSpec skills in both tool directories."""
    versions = {}
    for skills_dir in (OC_SKILLS, CL_SKILLS):
        for skill in skills_dir.glob("openspec-*/SKILL.md"):
            m = re.search(r'generatedBy:\s*"?([\w.\-]+)"?', skill.read_text(encoding="utf-8"))
            versions[skill.relative_to(ROOT).as_posix()] = m.group(1) if m else None
    return versions


def check(state):
    problems = []
    for path, (kind, value) in state.items():
        rel = path.relative_to(ROOT).as_posix()
        if kind == "symlink":
            if not path.is_symlink() or os.readlink(path) != value:
                problems.append(f"{rel}: expected symlink -> {value}")
        elif not path.is_file() or path.is_symlink() or path.read_text(encoding="utf-8") != value:
            problems.append(f"{rel}: out of sync with its source")
    for path in managed_existing():
        if path not in state:
            problems.append(f"{path.relative_to(ROOT).as_posix()}: stale (source was removed)")
    versions = openspec_versions()
    if len(set(versions.values())) > 1:
        detail = ", ".join(f"{k}={v}" for k, v in sorted(versions.items()))
        problems.append(f"OpenSpec files generated by different versions – run `openspec update`: {detail}")
    if len({p.split("/")[-2] for p in versions if p.startswith(".claude/")}) != \
            len({p.split("/")[-2] for p in versions if p.startswith(".opencode/")}):
        problems.append("OpenSpec skills differ between .claude/ and .opencode/ – run `openspec update`")
    return problems


def write(state):
    for path in managed_existing():
        if path not in state:
            path.unlink()
            print(f"removed  {path.relative_to(ROOT).as_posix()}")
    for path, (kind, value) in state.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.is_symlink() or (kind == "symlink" and path.exists()):
            path.unlink()
        if kind == "symlink":
            path.symlink_to(value)
        else:
            path.write_text(value, encoding="utf-8")
        print(f"synced   {path.relative_to(ROOT).as_posix()}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="only verify, exit 1 on drift")
    args = parser.parse_args()
    state = expected_state()
    if not args.check:
        write(state)
    problems = check(state)
    if problems:
        print("Agent configuration drift detected:")
        for p in problems:
            print(f"  ✗ {p}")
        print("Fix: python3 scripts/sync_agent_config.py (and `openspec update` for OpenSpec files)")
        sys.exit(1)
    print("✓ OpenCode and Claude Code configuration in sync")


if __name__ == "__main__":
    main()
