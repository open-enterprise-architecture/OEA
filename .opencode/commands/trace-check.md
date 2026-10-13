# /trace-check – Check the linkage consistency of all artifacts

Checks the consistency of all requirements engineering artifacts in the OEA repository.

## What is checked

1. **Count**: SH / UC / REQ / US / ADR
2. **UC completeness**: every UC has `primary_actor`, acceptance criteria, concept reference
3. **REQ completeness**: every REQ has `type`, `status`, UC reference
4. **US completeness**: every US has a UC reference and a REQ reference
5. **ADR completeness**: every ADR has `Deciders`
6. **Orphaned references**: check US→REQ and US→UC for existence
7. **Stale references**: "Owner of the repository" and "Lukas (Repository Owner)"
8. **Link validation**: `python3 scripts/validate_links.py`

## Execution

Run the following bash commands and report the result as a table:

```bash
cd /run/host/home/lukas/Projects/oea

# Count
echo "SH: $(ls business-analysis/stakeholders/SH-*.md | wc -l)"
echo "UC: $(ls requirements/use-cases/UC-*.md | wc -l)"
echo "REQ: $(ls requirements/req/REQ-*.md | wc -l)"
echo "US: $(ls requirements/user-stories/US-*.md | wc -l)"
echo "ADR: $(ls adrs/ADR-*.md | wc -l)"

# UC completeness
for f in requirements/use-cases/UC-*.md; do
  grep -q "^primary_actor:" "$f" || echo "MISSING primary_actor: $f"
  grep -q "Acceptance Criteria" "$f" || echo "MISSING AC: $f"
  grep -qi "concept\|§" "$f" || echo "MISSING concept reference: $f"
done

# REQ completeness
for f in requirements/req/REQ-*.md; do
  grep -q "^type:" "$f" || echo "MISSING type: $f"
  grep -q "^status:" "$f" || echo "MISSING status: $f"
  grep -q "UC-" "$f" || echo "MISSING UC-Ref: $f"
done

# US completeness + orphaned refs
for f in requirements/user-stories/US-*.md; do
  grep -q "UC-" "$f" || echo "MISSING UC-Ref: $f"
  grep -q "REQ-" "$f" || echo "MISSING REQ-Ref: $f"
  for req in $(grep -o "REQ-[0-9]*" "$f"); do
    num=${req#REQ-}
    ls requirements/req/REQ-${num}-*.md >/dev/null 2>&1 || echo "BROKEN REQ-Ref: $f → $req"
  done
  for uc in $(grep -o "UC-[0-9]*" "$f"); do
    num=${uc#UC-}
    ls requirements/use-cases/UC-${num}-*.md >/dev/null 2>&1 || echo "BROKEN UC-Ref: $f → $uc"
  done
done

# ADR completeness
for f in adrs/ADR-*.md; do
  grep -q "Deciders" "$f" || echo "MISSING Deciders: $f"
done

# Stale references
grep -rn "Owner of the repository\|Lukas (Repository Owner)" adrs/ requirements/ business-analysis/ || echo "No stale references"

# Link validation
python3 scripts/validate_links.py
```

## Output format

Table with count and ✅ / ⚠️ / ❌ per category. List warnings individually.
