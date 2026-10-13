#!/usr/bin/env python3
"""Generate requirements/traceability.md from the UC, REQ and US files.

The matrix is derived data – do not edit requirements/traceability.md by hand.

Usage (from the repository root):
  python3 scripts/generate_traceability.py          # write the matrix
  python3 scripts/generate_traceability.py --check  # CI: exit 1 if outdated
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REQ_DIR = ROOT / "requirements"
OUT = REQ_DIR / "traceability.md"

ID_RE = {k: re.compile(rf"\b{k}-\d+\b") for k in ("UC", "REQ", "US", "ADR")}


def split_frontmatter(text):
    """Return (frontmatter dict of scalars and lists, body)."""
    if not text.startswith("---\n"):
        return {}, text
    end = text.index("\n---", 4)
    data, key, parent = {}, None, None
    for line in text[4:end].splitlines():
        m = re.match(r"^(\s*)([A-Za-z_]+):\s*(.*)$", line)
        item = re.match(r"^\s*-\s+(.*)$", line)
        if m:
            indent, k, v = len(m.group(1)), m.group(2), m.group(3).strip().strip('"')
            if indent == 0:
                parent = k
            key = k if indent == 0 else f"{parent}.{k}"
            data[key] = v if v and v != "[]" else []
        elif item and key:
            if not isinstance(data.get(key), list):
                data[key] = []
            data[key].append(item.group(1).strip().strip('"'))
    return data, text[end + 4:]


def bold_field(body, name):
    m = re.search(rf"^\*\*{name}\*\*:\s*(.+)$", body, re.M)
    return m.group(1).strip() if m else ""


def heading_title(body, prefix):
    m = re.search(rf"^# {prefix}-\d+:\s*(.+)$", body, re.M)
    return m.group(1).strip() if m else ""


def num(id_):
    return int(id_.split("-")[1])


def load():
    ucs, reqs, uss = {}, {}, {}
    for f in sorted((REQ_DIR / "use-cases").glob("UC-*.md")):
        fm, body = split_frontmatter(f.read_text(encoding="utf-8"))
        uid = fm.get("id") or f.stem[:5]
        concept = fm.get("references.concept") or []
        ucs[uid] = {
            "file": f, "title": fm.get("title") or heading_title(body, "UC"),
            "actor": fm.get("primary_actor", ""), "prio": fm.get("priority", ""),
            "status": fm.get("status", ""),
            "concept": [c for c in concept if c.startswith("concept/")],
            "adrs": sorted(set(ID_RE["ADR"].findall(body)), key=num),
        }
    for f in sorted((REQ_DIR / "req").glob("REQ-*.md")):
        fm, body = split_frontmatter(f.read_text(encoding="utf-8"))
        rid = fm.get("id") or f.stem[:7]
        uc_refs = fm.get("references.use_cases") or fm.get("use_case") or []
        if isinstance(uc_refs, str):
            uc_refs = [uc_refs]
        reqs[rid] = {
            "file": f, "title": fm.get("title") or heading_title(body, "REQ"),
            "type": fm.get("type", ""), "prio": fm.get("priority", ""),
            "status": fm.get("status", ""), "ucs": [u for u in uc_refs if u.startswith("UC-")],
        }
    for f in sorted((REQ_DIR / "user-stories").glob("US-*.md")):
        fm, body = split_frontmatter(f.read_text(encoding="utf-8"))
        sid = fm.get("id") or f.stem[:6]
        if fm:
            req_refs = [fm["requirement"]] if fm.get("requirement") else []
            uc_refs = [fm["use_case"]] if fm.get("use_case") else []
            sp, status = fm.get("story_points", ""), fm.get("status", "")
        else:
            ref = body.split("## Acceptance Criteria")[0]
            req_refs = ID_RE["REQ"].findall(bold_field(ref, "Requirements") + " " + bold_field(ref, "Requirement"))
            uc_refs = ID_RE["UC"].findall(bold_field(ref, "Use Case"))
            sp, status = bold_field(body, "Story Points"), bold_field(body, "Status")
        uss[sid] = {
            "file": f, "title": fm.get("title") or heading_title(body, "US"),
            "reqs": sorted(set(req_refs), key=num), "ucs": sorted(set(uc_refs), key=num),
            "sp": int(sp) if str(sp).isdigit() else 0, "status": status,
        }
    return ucs, reqs, uss


def link(id_, path, base=REQ_DIR):
    return f"[{id_}]({path.relative_to(base).as_posix()})"


def concept_label(path):
    m = re.search(r"/(\d+)-([\w-]+)\.md$", path)
    if not m:
        return path
    target = ROOT / path
    title = ""
    if target.exists():
        h = re.search(r"^#{1,2}\s+\d+\.\s+(.+)$", target.read_text(encoding="utf-8"), re.M)
        title = h.group(1).strip() if h else ""
    return f"[§{int(m.group(1))} {title}](../{path})" if title else f"[§{int(m.group(1))}](../{path})"


def render(ucs, reqs, uss):
    req_to_us = defaultdict(list)
    for sid, s in uss.items():
        for r in s["reqs"]:
            req_to_us[r].append(sid)
    uc_to_req = defaultdict(list)
    for rid, r in reqs.items():
        for u in r["ucs"]:
            uc_to_req[u].append(rid)
    adr_files = {p.stem[:7]: p for p in (ROOT / "adrs").glob("ADR-*.md")}

    out = [
        "# Traceability Matrix",
        "",
        "<!-- GENERATED by scripts/generate_traceability.py – do not edit by hand -->",
        "",
        "Traceability from use cases through requirements to user stories, derived from the",
        "frontmatter and reference sections of the artifacts.",
        "",
        f"**Total UCs**: {len(ucs)} | **Total REQs**: {len(reqs)} | **Total USs**: {len(uss)}",
        "",
        "---",
        "",
        "## 1. UC × REQ × US",
        "",
    ]
    for uid in sorted(ucs, key=num):
        u = ucs[uid]
        out += [f"### {link(uid, u['file'])}: {u['title']}", "",
                f"**Primary Actor**: {u['actor'] or '–'} | **Priority**: {u['prio'] or '–'} | **Status**: {u['status'] or '–'}", ""]
        rids = sorted(uc_to_req.get(uid, []), key=num)
        if not rids:
            out += ["_No requirements reference this use case._", ""]
            continue
        out += ["| REQ | Title | Type | Priority | USs |", "|---|---|---|---|---|"]
        for rid in rids:
            r = reqs[rid]
            us_links = ", ".join(link(s, uss[s]["file"]) + (" _(rejected)_" if uss[s]["status"] == "rejected" else "")
                                 for s in sorted(req_to_us.get(rid, []), key=num)) or "–"
            title = r["title"] + (" _(rejected)_" if r["status"] == "rejected" else "")
            out.append(f"| {link(rid, r['file'])} | {title} | {r['type']} | {r['prio']} | {us_links} |")
        out.append("")

    out += ["## 2. UC × Concept × ADR", "", "| UC | Concept Chapters | ADRs |", "|---|---|---|"]
    for uid in sorted(ucs, key=num):
        u = ucs[uid]
        concept = ", ".join(concept_label(c) for c in u["concept"]) or "–"
        adrs = ", ".join(f"[{a}](../adrs/{adr_files[a].name})" if a in adr_files else a for a in u["adrs"]) or "–"
        out.append(f"| {link(uid, u['file'])} | {concept} | {adrs} |")
    out.append("")

    types = defaultdict(int)
    for r in reqs.values():
        types[r["type"] or "(none)"] += 1
    out += ["## 3. REQ Type Overview", "", "| Type | Count |", "|---|---|"]
    out += [f"| {t} | {n} |" for t, n in sorted(types.items(), key=lambda x: (-x[1], x[0]))]
    out += ["", "## 4. Story Points per Use Case", "", "| UC | USs | Story Points |", "|---|---|---|"]
    total = 0
    for uid in sorted(ucs, key=num):
        sids = [s for s, v in uss.items() if uid in v["ucs"] and v["status"] != "rejected"]
        sp = sum(uss[s]["sp"] for s in sids)
        total += sp
        out.append(f"| {uid} | {len(sids)} | {sp} |")
    out += [f"| **Total** | | **{total}** |", ""]

    gaps = [
        ("REQs without US", [link(r, reqs[r]["file"]) for r in sorted(reqs, key=num)
                             if r not in req_to_us and reqs[r]["status"] != "rejected"]),
        ("REQs without UC reference", [link(r, reqs[r]["file"]) for r in sorted(reqs, key=num) if not reqs[r]["ucs"]]),
        ("REQs referencing an unknown UC", [f"{link(r, reqs[r]['file'])} → {', '.join(u for u in reqs[r]['ucs'] if u not in ucs)}"
                                            for r in sorted(reqs, key=num) if any(u not in ucs for u in reqs[r]["ucs"])]),
        ("USs without REQ reference", [link(s, uss[s]["file"]) for s in sorted(uss, key=num) if not uss[s]["reqs"]]),
        ("USs referencing an unknown REQ", [f"{link(s, uss[s]['file'])} → {', '.join(r for r in uss[s]['reqs'] if r not in reqs)}"
                                            for s in sorted(uss, key=num) if any(r not in reqs for r in uss[s]["reqs"])]),
        ("UCs without concept reference", [link(u, ucs[u]["file"]) for u in sorted(ucs, key=num) if not ucs[u]["concept"]]),
        ("UCs without primary actor", [link(u, ucs[u]["file"]) for u in sorted(ucs, key=num) if not ucs[u]["actor"]]),
    ]
    out += ["## 5. Coverage Gaps", ""]
    for title, items in gaps:
        out += [f"### {title}", "", ", ".join(items) if items else "None.", ""]

    referenced = {c for u in ucs.values() for c in u["concept"]}
    chapters = sorted(p.relative_to(ROOT).as_posix() for p in (ROOT / "concept").glob("*/[0-9][0-9]-*.md"))
    orphans = [concept_label(c) for c in chapters if c not in referenced]
    out += ["## 6. Concept Chapters Without Use Case", "",
            ", ".join(orphans) if orphans else "None.", ""]
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="exit 1 if the matrix is outdated")
    args = parser.parse_args()
    content = render(*load())
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if current != content:
            print("✗ requirements/traceability.md is outdated – run python3 scripts/generate_traceability.py")
            sys.exit(1)
        print("✓ requirements/traceability.md is up to date")
        return
    OUT.write_text(content, encoding="utf-8")
    print(f"✓ wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
