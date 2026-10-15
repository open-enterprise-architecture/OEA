#!/usr/bin/env python3
"""Generate and check THIRD-PARTY-LICENSES.md (ADR-043).

The list covers the components distributed with OEA: the runtime libraries of the backend
jar (Maven license report) and the packages of the frontend bundle (manifest written by the
Vite build), plus the base images of the container images as runtime environment. Every
distributed component must have an allowed license (ADR-043); a dual-licensed component
needs one allowed alternative.

Inputs (create them first):
    cd backend && ./mvnw -B license:add-third-party   # backend/target/generated-sources/license/THIRD-PARTY.txt
    cd frontend && npm run build                      # frontend/dist/third-party-packages.json

Usage:
    python3 scripts/generate_third_party_licenses.py           # write THIRD-PARTY-LICENSES.md
    python3 scripts/generate_third_party_licenses.py --check   # fail on forbidden licenses or an outdated file
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "THIRD-PARTY-LICENSES.md"
BACKEND_REPORT = ROOT / "backend/target/generated-sources/license/THIRD-PARTY.txt"
FRONTEND_MANIFEST = ROOT / "frontend/dist/third-party-packages.json"
DOCKERFILE = ROOT / "backend/Dockerfile"
COMPOSE = ROOT / "compose.yaml"

# ADR-043: allowed licenses of distributed components, in order of preference for
# dual-licensed components (permissive before weak copyleft). Changing the categories
# needs an amendment of ADR-043.
PERMISSIVE = ["MIT", "Apache-2.0", "BSD-3-Clause", "BSD-2-Clause", "ISC", "0BSD", "CC0-1.0", "Unlicense",
              "BlueOak-1.0.0", "PostgreSQL"]
WEAK_COPYLEFT = ["EPL-2.0", "EPL-1.0", "MPL-2.0", "LGPL-2.1-only", "LGPL-2.1-or-later", "LGPL-3.0-only",
                 "LGPL-3.0-or-later"]
ALLOWED = PERMISSIVE + WEAK_COPYLEFT

# License names as published in POMs, mapped to SPDX identifiers
NAMES = {
    "apache license, version 2.0": "Apache-2.0",
    "the apache software license, version 2.0": "Apache-2.0",
    "apache license 2.0": "Apache-2.0",
    "apache license version 2.0": "Apache-2.0",
    "the apache license, version 2.0": "Apache-2.0",
    "apache-2.0": "Apache-2.0",
    "mit": "MIT",
    "the mit license": "MIT",
    "mit license": "MIT",
    # MIT-style license of the Legion of the Bouncy Castle
    "bouncy castle licence": "MIT",
    "bsd-2-clause": "BSD-2-Clause",
    "bsd-3-clause": "BSD-3-Clause",
    # The Eclipse Distribution License 1.0 is the BSD-3-Clause license (SPDX)
    "eclipse distribution license - v 1.0": "BSD-3-Clause",
    "eclipse distribution license v. 1.0": "BSD-3-Clause",
    "edl 1.0": "BSD-3-Clause",
    "epl-2.0": "EPL-2.0",
    "epl 2.0": "EPL-2.0",
    "eclipse public license - v 2.0": "EPL-2.0",
    "eclipse public license v. 2.0": "EPL-2.0",
    "lgpl-2.1-only": "LGPL-2.1-only",
    "gpl2 w/ cpe": "GPL-2.0-with-classpath-exception",
    "public domain, per creative commons cc0": "CC0-1.0",
}

# Libraries the Spring Boot plugin adds to the jar without a Maven dependency
BOOT_PLUGIN_LIBRARIES = [
    ("Spring Boot Jarmode Tools", "org.springframework.boot:spring-boot-jarmode-tools", "Apache-2.0",
     "https://spring.io/projects/spring-boot"),
]

# Main licenses of the base images (runtime environment, ADR-043 point 4)
IMAGE_LICENSES = {
    "eclipse-temurin": ("Eclipse Temurin JRE (OpenJDK) on Ubuntu",
                        "GPL-2.0-with-classpath-exception (JRE); operating system packages under their own licenses"),
    "postgres": ("PostgreSQL on Alpine Linux",
                 "PostgreSQL (database); operating system packages under their own licenses"),
}


@dataclass(frozen=True)
class Component:
    name: str
    coordinates: str
    version: str
    licenses: tuple[str, ...]
    url: str

    @property
    def chosen(self) -> str | None:
        for license_id in ALLOWED:
            if license_id in self.licenses:
                return license_id
        return None


def spdx(name: str) -> str:
    return NAMES.get(name.strip().lower(), name.strip())


def backend_components(report: Path) -> list[Component]:
    line = re.compile(r"^\s+((?:\([^)]*\)\s*)+)(.+?) \(([^:\s]+):([^:\s]+):([^\s]+) - (.*)\)\s*$")
    components = []
    for text in report.read_text(encoding="utf-8").splitlines():
        match = line.match(text)
        if not match:
            continue
        licenses = tuple(spdx(name) for name in re.findall(r"\(([^)]*)\)", match.group(1)))
        group, artifact, version = match.group(3), match.group(4), match.group(5)
        components.append(Component(match.group(2).strip(), f"{group}:{artifact}", version, licenses,
                                    match.group(6).strip()))
    if not components:
        raise SystemExit(f"ERROR no dependencies found in {report}")
    boot_version = next((c.version for c in components if c.coordinates.startswith("org.springframework.boot:")), "")
    for name, coordinates, license_id, url in BOOT_PLUGIN_LIBRARIES:
        components.append(Component(name, coordinates, boot_version, (license_id,), url))
    return sorted(components, key=lambda c: (c.coordinates, c.version))


def frontend_components(manifest: Path) -> list[Component]:
    components = []
    for package in json.loads(manifest.read_text(encoding="utf-8")):
        expression = package["license"].strip("()")
        licenses = tuple(spdx(part) for part in re.split(r"\s+OR\s+", expression))
        components.append(Component(package["name"], package["name"], package["version"], licenses,
                                    package.get("repository", "")))
    return sorted(components, key=lambda c: (c.coordinates, c.version))


def base_images() -> list[tuple[str, str, str]]:
    images = []
    stages = re.findall(r"^FROM\s+(\S+)", DOCKERFILE.read_text(encoding="utf-8"), flags=re.M)
    references = [stages[-1]] if stages else []
    references += re.findall(r"^\s*image:\s*(docker\.io/\S+)", COMPOSE.read_text(encoding="utf-8"), flags=re.M)
    for reference in references:
        name = reference.split("/")[-1].split(":")[0].split("@")[0]
        description, license_text = IMAGE_LICENSES.get(name, (name, "UNKNOWN"))
        images.append((description, reference.split("@")[0], license_text))
    return images


def violations(components: list[Component]) -> list[str]:
    return [f"{c.coordinates} {c.version}: {' OR '.join(c.licenses)} is not allowed (ADR-043)"
            for c in components if c.chosen is None]


def table(components: list[Component]) -> list[str]:
    rows = ["| Component | Version | License | Alternatives | Project |", "|---|---|---|---|---|"]
    for c in components:
        others = ", ".join(license_id for license_id in c.licenses if license_id != c.chosen) or "–"
        project = f"<{c.url}>" if c.url.startswith("http") else (c.url or "–")
        rows.append(f"| {c.name} (`{c.coordinates}`) | {c.version} | {c.chosen} | {others} | {project} |")
    return rows


def render(backend: list[Component], frontend: list[Component], images: list[tuple[str, str, str]]) -> str:
    counts = Counter(c.chosen for c in backend + frontend)
    lines = [
        "# Third-Party Licenses",
        "",
        "<!-- Generated by scripts/generate_third_party_licenses.py – do not edit by hand. -->",
        "",
        "OEA ships the third-party components listed below. Each component remains under its own license,"
        " which grants its rights directly to every recipient; neither the AGPL-3.0 nor the Enterprise license"
        " of OEA extends to them (see [NOTICE](NOTICE)). Only permissive licenses and weak-copyleft licenses of"
        " unmodified libraries are allowed for distributed components"
        " ([ADR-043](adrs/ADR-043-third-party-license-policy.md)); for a component offered under several"
        " licenses, OEA uses it under the license named in the column *License*.",
        "",
        "License texts and notices: the backend jar contains the license and notice files of its libraries"
        " (`BOOT-INF/lib`); the frontend ships the license texts of its bundled packages as"
        " `third-party-licenses.txt`. The source code of the weak-copyleft components is available from the"
        " projects listed.",
        "",
        "## Summary",
        "",
        "| License | Components |",
        "|---|---|",
    ]
    for license_id in ALLOWED:
        if counts.get(license_id):
            lines.append(f"| {license_id} | {counts[license_id]} |")
    lines += ["", f"## Backend Runtime Libraries ({len(backend)})", ""] + table(backend)
    lines += ["", f"## Frontend Bundle ({len(frontend)})", ""] + table(frontend)
    lines += [
        "",
        "## Runtime Environment (Container Base Images)",
        "",
        "The container images are built on these base images. OEA does not link against the operating system;"
        " the GPL-2.0 of the Java runtime carries the Classpath Exception. They are listed for information and are"
        " not subject to the library rule of ADR-043.",
        "",
        "| Image | Reference | Licenses |",
        "|---|---|---|",
    ]
    lines += [f"| {description} | `{reference}` | {license_text} |" for description, reference, license_text in images]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate and check THIRD-PARTY-LICENSES.md (ADR-043).")
    parser.add_argument("--check", action="store_true", help="fail if the file is outdated or a license is not allowed")
    parser.add_argument("--backend-report", type=Path, default=BACKEND_REPORT)
    parser.add_argument("--frontend-manifest", type=Path, default=FRONTEND_MANIFEST)
    args = parser.parse_args()

    for path, hint in ((args.backend_report, "cd backend && ./mvnw -B license:add-third-party"),
                       (args.frontend_manifest, "cd frontend && npm run build")):
        if not path.exists():
            print(f"ERROR {path} is missing; create it with: {hint}", file=sys.stderr)
            return 2
    backend = backend_components(args.backend_report)
    frontend = frontend_components(args.frontend_manifest)
    images = base_images()
    problems = violations(backend + frontend)
    problems += [f"base image {reference}: no license entry" for _, reference, text in images if text == "UNKNOWN"]
    content = render(backend, frontend, images)

    if args.check:
        if problems:
            print("✗ Third-party license check failed:", file=sys.stderr)
            for problem in problems:
                print(f"  - {problem}", file=sys.stderr)
            return 1
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if current != content:
            print("✗ THIRD-PARTY-LICENSES.md is outdated; run python3 scripts/generate_third_party_licenses.py",
                  file=sys.stderr)
            return 1
        print(f"✓ {len(backend)} backend libraries and {len(frontend)} frontend packages with allowed licenses;"
              " THIRD-PARTY-LICENSES.md is up to date")
        return 0

    OUTPUT.write_text(content, encoding="utf-8")
    for problem in problems:
        print(f"WARNING {problem}", file=sys.stderr)
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {len(backend)} backend libraries, {len(frontend)} frontend packages")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
