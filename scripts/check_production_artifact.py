#!/usr/bin/env python3
"""Inspect the production artifact for test-only authentication code (ADR-036 §7, REQ-183 AC1).

Fails if the Spring Boot jar or the container image contains
  - classes whose name matches the stub pattern (e.g. FakeJwtIssuer)
  - banned libraries in BOOT-INF/lib (Testcontainers, Keycloak, mock OAuth servers,
    WireMock, spring-security-test, test frameworks)
  - development configuration (application-dev*) or IdP realm/blueprint files
  - private keys (*.pem, *.key, *.jks, *.p12)
  - any known development secret value (literals of compose.dev.yaml and dev/)

Usage:
    python3 scripts/check_production_artifact.py --jar backend/target/oea-backend-*.jar
    python3 scripts/check_production_artifact.py --image oea-backend:local
"""

from __future__ import annotations

import argparse
import fnmatch
import io
import re
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_dev_secrets  # noqa: E402

STUB_CLASS = re.compile(r"(Stub|Fake|Mock|Dummy|Test)(Jwt|Token|Auth|Idp|Issuer|Login)[^/]*\.class$")
BANNED_LIBRARIES = [
    "testcontainers-*", "spring-boot-testcontainers-*", "keycloak-*", "testcontainers-keycloak-*",
    "mock-oauth2-server-*", "wiremock-*", "spring-security-test-*", "junit-*", "mockito-*",
    "archunit-*", "cucumber-*", "spring-boot-test-*", "spring-test-*",
]
FORBIDDEN_FILES = ["application-dev*", "*realm*.json", "*blueprint*.yaml", "*blueprint*.yml",
                   "*.pem", "*.key", "*.jks", "*.p12", "*.pfx"]
TEXT_SUFFIXES = (".yml", ".yaml", ".properties", ".json", ".xml", ".txt", ".sql", ".conf", ".env")


def development_secrets(root: Path) -> set[str]:
    return {value for _, _, value in check_dev_secrets.find_secrets(root) if len(value) >= 6}


def inspect_jar(name: str, data: bytes, secrets: set[str]) -> list[str]:
    findings = []
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        for entry in jar.namelist():
            base = entry.rsplit("/", 1)[-1]
            if entry.startswith("BOOT-INF/classes/") and STUB_CLASS.search(entry):
                findings.append(f"{name}: stub class {entry}")
            if entry.startswith("BOOT-INF/lib/") and any(fnmatch.fnmatch(base, p) for p in BANNED_LIBRARIES):
                findings.append(f"{name}: banned library {base}")
            if base and any(fnmatch.fnmatch(base, p) for p in FORBIDDEN_FILES):
                findings.append(f"{name}: forbidden file {entry}")
            if entry.startswith("BOOT-INF/classes/") and entry.endswith(TEXT_SUFFIXES):
                text = jar.read(entry).decode("utf-8", errors="replace")
                if any(secret in text for secret in secrets):
                    findings.append(f"{name}: {entry} contains a known development secret")
    return findings


def inspect_image(image: str, secrets: set[str]) -> list[str]:
    container = subprocess.run(["docker", "create", image], capture_output=True, text=True, check=True).stdout.strip()
    try:
        exported = subprocess.run(["docker", "export", container], capture_output=True, check=True).stdout
    finally:
        subprocess.run(["docker", "rm", container], capture_output=True)
    findings = []
    jars = 0
    with tarfile.open(fileobj=io.BytesIO(exported)) as tar:
        for member in tar:
            if not member.isfile():
                continue
            path = member.name
            base = path.rsplit("/", 1)[-1]
            if path.startswith(("app/", "./app/", "workspace/", "config/")) and any(
                    fnmatch.fnmatch(base, p) for p in FORBIDDEN_FILES):
                findings.append(f"{image}: forbidden file /{path}")
            if path.startswith(("app/", "./app/")) and base.endswith(".jar"):
                jars += 1
                findings += inspect_jar(f"{image}:/{path}", tar.extractfile(member).read(), secrets)
    if jars == 0:
        findings.append(f"{image}: no application jar found under /app")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", type=Path, action="append", default=[])
    parser.add_argument("--image", action="append", default=[])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    if not args.jar and not args.image:
        parser.error("give --jar and/or --image")

    secrets = development_secrets(args.root)
    findings = []
    for jar in args.jar:
        findings += inspect_jar(str(jar), jar.read_bytes(), secrets)
    for image in args.image:
        findings += inspect_image(image, secrets)

    if findings:
        print(f"✗ {len(findings)} forbidden item(s) in the production artifact:")
        print("\n".join(f"  {f}" for f in findings))
        return 1
    checked = ", ".join([str(j) for j in args.jar] + args.image)
    print(f"✓ No test-only authentication code, development files or secrets in {checked}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
