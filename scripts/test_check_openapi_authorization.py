#!/usr/bin/env python3
"""Unit tests of the 429 rule of check_openapi_authorization.py (change s3-throttling-and-denials).

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


if __name__ == "__main__":
    unittest.main()
