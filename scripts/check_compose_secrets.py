#!/usr/bin/env python3
"""Lint the Compose files against the secret and exposure rules of ADR-036 §3/§7.

compose.yaml (operator file):
  - no literal secret: every secret-like key gets its value from ${VAR:?message}
  - no ${VAR:-default} or ${VAR-default} for a secret variable
  - `docker compose -f compose.yaml config` fails with an empty environment
compose.dev.yaml (development file):
  - every service requires the Compose profile "dev"
  - every published port is bound to 127.0.0.1

The Docker-based checks need the docker CLI with the Compose plugin; without it they
are skipped with a warning (CI always runs them).

Usage:
    python3 scripts/check_compose_secrets.py [--root DIR]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

SECRET_NAME = re.compile(r"(?i)(password|passwd|pwd|secret|token|credential|private[_-]?key|api[_-]?key|kek)")
KEY_VALUE = re.compile(r"""^\s*-?\s*["']?(?P<key>[A-Za-z0-9_.\-]+)["']?\s*[:=]\s*(?P<value>.*?)\s*$""")
REQUIRED_REF = re.compile(r"""^["']?\$\{[A-Za-z0-9_]+:?\?[^}]*\}["']?$""")
DEFAULT_REF = re.compile(r"\$\{(?P<name>[A-Za-z0-9_]+)(?P<op>:?-)")


def check_operator_file(path: Path) -> list[str]:
    errors = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        for match in DEFAULT_REF.finditer(line):
            if SECRET_NAME.search(match.group("name")):
                errors.append(f"{path.name}:{number}: secret variable {match.group('name')} has a default "
                              f"(${{VAR{match.group('op')}…}}); use ${{VAR:?message}}")
        kv = KEY_VALUE.match(line)
        if not kv or not SECRET_NAME.search(kv.group("key")):
            continue
        value = re.sub(r"\s+#.*$", "", kv.group("value"))
        if value in ("", "|", ">") or value.startswith(("[", "{")):
            continue
        if not REQUIRED_REF.match(value) and not DEFAULT_REF.search(value):
            errors.append(f"{path.name}:{number}: {kv.group('key')} is not taken from a required variable "
                          f"(${{VAR:?message}})")
    return errors


def compose(root: Path, *args: str, env: dict | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(["docker", "compose", *args], cwd=root, env=env, capture_output=True, text=True)


def check_operator_empty_environment(root: Path, path: Path) -> list[str]:
    env = {"PATH": os.environ.get("PATH", ""), "HOME": os.environ.get("HOME", "")}
    if "DOCKER_HOST" in os.environ:
        env["DOCKER_HOST"] = os.environ["DOCKER_HOST"]
    result = compose(root, "-f", str(path), "--env-file", os.devnull, "config", "--quiet", env=env)
    if result.returncode == 0:
        return [f"{path.name}: `docker compose config` succeeds with an empty environment; "
                "every secret must be a required variable"]
    return []


def check_development_file(root: Path, path: Path) -> list[str]:
    errors = []
    normalized = compose(root, "-f", str(path), "--profile", "*", "config", "--format", "json")
    if normalized.returncode != 0:
        return errors + [f"{path.name}: docker compose config failed: {normalized.stderr.strip()}"]
    services = json.loads(normalized.stdout).get("services", {})
    for name, service in sorted(services.items()):
        if "dev" not in (service.get("profiles") or []):
            errors.append(f"{path.name}: service {name} lacks the Compose profile \"dev\"")
        for port in service.get("ports") or []:
            if port.get("published") and port.get("host_ip") != "127.0.0.1":
                errors.append(f"{path.name}: service {name} publishes port {port.get('published')} on "
                              f"{port.get('host_ip') or 'all interfaces'}; bind it to 127.0.0.1")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = args.root.resolve()
    operator, development = root / "compose.yaml", root / "compose.dev.yaml"

    errors: list[str] = []
    for path in (operator, development):
        if not path.exists():
            errors.append(f"{path.name} is missing")
    if errors:
        print("\n".join(f"✗ {e}" for e in errors))
        return 1

    errors += check_operator_file(operator)
    if shutil.which("docker") and compose(root, "version").returncode == 0:
        errors += check_operator_empty_environment(root, operator)
        errors += check_development_file(root, development)
    else:
        print("⚠ docker compose not available: skipped the empty-environment, profile and port checks")

    if errors:
        print(f"✗ {len(errors)} Compose violation(s):")
        print("\n".join(f"  {e}" for e in errors))
        return 1
    print("✓ compose.yaml and compose.dev.yaml follow the secret and exposure rules")
    return 0


if __name__ == "__main__":
    sys.exit(main())
