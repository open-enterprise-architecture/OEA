#!/usr/bin/env python3
"""Unit tests of check_openapi_authorization.py: the 429 rule (change s3-throttling-and-denials)
and the status of the security schemes (change s11-review-hardening).

Usage: python3 scripts/test_check_openapi_authorization.py
"""

from __future__ import annotations

import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_openapi_authorization as check  # noqa: E402

SPEC = Path(__file__).resolve().parents[1] / "api" / "openapi.yaml"
RATE_LIMIT_LINE = "        '429': { $ref: '#/components/responses/TooManyRequests' }\n"


def run(text: str) -> tuple[int, str]:
    with tempfile.TemporaryDirectory() as tmp:
        spec = Path(tmp) / "openapi.yaml"
        spec.write_text(text, encoding="utf-8")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = check.main(["check", str(spec)])
    return code, out.getvalue()


def without_rate_limit(text: str, operation_id: str) -> str:
    """Remove the 429 line of the given operation."""
    start = text.index(f"operationId: {operation_id}\n")
    position = text.index(RATE_LIMIT_LINE, start)
    return text[:position] + text[position + len(RATE_LIMIT_LINE):]


class RateLimitRuleTests(unittest.TestCase):

    def setUp(self) -> None:
        self.text = SPEC.read_text(encoding="utf-8")

    def test_contract_is_compliant(self) -> None:
        code, out = run(self.text)
        self.assertEqual(code, 0, out)

    def test_authenticated_operation_without_429_fails(self) -> None:
        code, out = run(without_rate_limit(self.text, "queryCatalog"))
        self.assertEqual(code, 1)
        self.assertIn("queryCatalog", out)
        self.assertIn("missing '429' response", out)

    def test_setup_without_429_fails(self) -> None:
        code, out = run(without_rate_limit(self.text, "completeSetup"))
        self.assertEqual(code, 1)
        self.assertIn("completeSetup", out)


def scheme_status(text: str, scheme: str, status: str | None) -> str:
    """Set (or with ``None`` remove) the x-oea-status line of a security scheme."""
    start = text.index(f"\n    {scheme}:\n", text.index("  securitySchemes:")) + 1
    end = text.index("      description:", start)
    block = "".join(line for line in text[start:end].splitlines(keepends=True)
                    if not line.startswith("      x-oea-status:"))
    if status is not None:
        block += f"      x-oea-status: {status}\n"
    return text[:start] + block + text[end:]


class SecuritySchemeStatusTests(unittest.TestCase):

    def setUp(self) -> None:
        self.text = SPEC.read_text(encoding="utf-8")

    def test_contract_marks_the_planned_schemes(self) -> None:
        code, out = run(self.text)
        self.assertEqual(code, 0, out)
        self.assertIn("security schemes accepted: cookieAuth, planned: bearerAuth, clientSecretBasic, "
                      "oauth2ClientCredentials", out)

    def test_scheme_without_marker_passes(self) -> None:
        code, out = run(scheme_status(self.text, "bearerAuth", None))
        self.assertEqual(code, 0, out)

    def test_other_status_fails_and_names_the_scheme(self) -> None:
        for status in ("implemented", "draft"):
            with self.subTest(status=status):
                code, out = run(scheme_status(self.text, "oauth2ClientCredentials", status))
                self.assertEqual(code, 1)
                self.assertIn(f"security scheme 'oauth2ClientCredentials': x-oea-status must be one of "
                              f"['planned'], got '{status}'", out)


if __name__ == "__main__":
    unittest.main()
