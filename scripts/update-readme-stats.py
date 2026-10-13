#!/usr/bin/env python3
"""
Update auto-generated sections in README.md.

Replaces content between <!-- AUTO-GENERATED: <name> --> and
<!-- /AUTO-GENERATED: <name> --> markers with current repo state.

Trigger files (see .git/hooks/pre-commit):
  adrs/ADR-*.md, business-analysis/stakeholders/SH-*.md,
  requirements/**/*.md, business-objects/*.md, concept/README.md
"""
import glob, os, re, sys
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
README = os.path.join(ROOT, 'README.md')


def count(pattern):
    return len(glob.glob(os.path.join(ROOT, pattern)))


def concept_version():
    path = os.path.join(ROOT, 'concept', 'README.md')
    try:
        with open(path) as f:
            m = re.search(r'v(\d+\.\d+)', f.read())
            return f'v{m.group(1)}' if m else '?'
    except FileNotFoundError:
        return '?'


def stakeholder_lines():
    lines = []
    for f in sorted(glob.glob(os.path.join(ROOT, 'business-analysis/stakeholders/SH-*.md'))):
        with open(f) as fh:
            content = fh.read()
        # H1 heading: "# Stakeholder: Name – Role" (or "# Stakeholder Profile: ...") or frontmatter name
        m = re.search(r'^# Stakeholder(?: Profile)?: (.+?)$', content, re.MULTILINE)
        if m:
            raw = m.group(1).strip()
            # Split "Name – Role" → bold name + role
            parts = re.split(r'\s*[–-]\s*', raw, maxsplit=1)
            name = parts[0].strip()
            role = parts[1].strip() if len(parts) > 1 else ''
        else:
            # Frontmatter fallback: "name: Name – Role"
            m2 = re.search(r'^name:\s*(.+?)$', content, re.MULTILINE)
            if m2:
                raw = m2.group(1).strip()
                parts = re.split(r'\s*[–-]\s*', raw, maxsplit=1)
                name = parts[0].strip()
                role = parts[1].strip() if len(parts) > 1 else ''
            else:
                continue
        sh_id = re.search(r'SH-(\d+)', os.path.basename(f)).group(0)
        entry = f'- **{sh_id}** {name}'
        if role:
            entry += f' – {role}'
        lines.append(entry)
    return lines


def adr_rows():
    rows = []
    for f in sorted(glob.glob(os.path.join(ROOT, 'adrs/ADR-*.md'))):
        base = os.path.basename(f)
        if 'template' in base.lower() or base == 'README.md':
            continue
        with open(f) as fh:
            content = fh.read()
        m_title = re.search(r'^# (ADR-\d+: .+?)$', content, re.MULTILINE)
        if not m_title:
            continue
        full_title = m_title.group(1).strip()
        # Shorten: remove "ADR-NNN: " prefix for display
        short = re.sub(r'^ADR-\d+:\s*', '', full_title)
        # Support both frontmatter (status: accepted) and bold (**Status**: accepted)
        m_status = re.search(r'^status:\s*(\w+)', content, re.MULTILINE) or \
                   re.search(r'^\*\*Status\*\*:?\s*(\w+)', content, re.MULTILINE)
        status = m_status.group(1).lower() if m_status else '?'
        adr_id = re.search(r'ADR-\d+', base).group(0)
        rel_path = f'adrs/{base}'
        rows.append(f'| [{adr_id}]({rel_path}) | {short} | {status} |')
    return rows


def build_stats_block():
    n_sh  = count('business-analysis/stakeholders/SH-*.md')
    n_uc  = count('requirements/use-cases/UC-*.md')
    n_req = count('requirements/req/REQ-*.md')
    n_us  = count('requirements/user-stories/US-*.md')
    n_adr = count('adrs/ADR-*.md')   # glob only matches ADR-NNN-*.md
    n_bo  = count('business-objects/*.md')
    ver   = concept_version()
    today = date.today().isoformat()
    return f"""\
| Artifact | Count |
|---|---|
| Stakeholder profiles | {n_sh} |
| Use Cases | {n_uc} |
| Requirements | {n_req} |
| User Stories | {n_us} |
| ADRs | {n_adr} |
| Business Objects | {n_bo} |

_Concept: {ver} · Last update: {today}._"""


def build_stakeholders_block():
    sh_lines = stakeholder_lines()
    n = len(sh_lines)
    header = f'{n} persona profiles have been worked out (see [`business-analysis/stakeholders/`](business-analysis/stakeholders/)):\n'
    return header + '\n' + '\n'.join(sh_lines)


def build_adrs_block():
    rows = adr_rows()
    header = '| ADR | Decision | Status |\n|---|---|---|'
    return header + '\n' + '\n'.join(rows)


BLOCKS = {
    'stats':        build_stats_block,
    'stakeholders': build_stakeholders_block,
    'adrs':         build_adrs_block,
}


def replace_block(content, name, new_body):
    pattern = (
        r'(<!-- AUTO-GENERATED: ' + re.escape(name) + r' -->)'
        r'.*?'
        r'(<!-- /AUTO-GENERATED: ' + re.escape(name) + r' -->)'
    )
    replacement = r'\1\n' + new_body + r'\n\2'
    result, n = re.subn(pattern, replacement, content, flags=re.DOTALL)
    if n == 0:
        print(f'  Warning: marker for "{name}" not found', file=sys.stderr)
    return result


def main():
    with open(README) as f:
        content = f.read()

    original = content
    for name, builder in BLOCKS.items():
        try:
            new_body = builder()
            content = replace_block(content, name, new_body)
        except Exception as e:
            print(f'  Error in block "{name}": {e}', file=sys.stderr)

    if content != original:
        with open(README, 'w') as f:
            f.write(content)
        print('README.md updated.')
    else:
        print('README.md unchanged.')


if __name__ == '__main__':
    main()
