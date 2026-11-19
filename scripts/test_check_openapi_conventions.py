#!/usr/bin/env python3
"""Unit tests of check_openapi_conventions.py (S17 change 8, API-8): the shared id parameter, the
string limits of request bodies and the names of page envelopes, each with a positive and a negative
example, the exemptions, an allowlisted entry and a stale allowlist entry.

Usage: python3 scripts/test_check_openapi_conventions.py
"""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_openapi_conventions as check  # noqa: E402

ID_REF = {"$ref": "#/components/parameters/UuidIdParam"}
ID_INLINE = {"name": "id", "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}}


def body(schema: dict) -> dict:
    return {"requestBody": {"content": {"application/json": {"schema": schema}}}}


def bundle(operations: dict | None = None, schemas: dict | None = None) -> dict:
    """A bundle with one path per operation id: {operationId: (method, operation)}."""
    paths = {}
    for operation_id, (method, operation) in (operations or {}).items():
        paths[f"/{operation_id}"] = {method: {"operationId": operation_id, **operation}}
    return {"paths": paths, "components": {"schemas": schemas or {}}}


def run(data: dict, ids: dict | None = None, gaps: dict | None = None, envelopes: dict | None = None) -> list[str]:
    return check.check(data, ids or {}, gaps or {}, envelopes or {})


def page(**members: dict) -> dict:
    return {"type": "object", "properties": {"page": {"$ref": "#/components/schemas/PageMeta"}, **members}}


class IdParameterTests(unittest.TestCase):
    def test_reference_is_accepted(self) -> None:
        self.assertEqual(run(bundle({"a": ("get", {"parameters": [ID_REF]})})), [])

    def test_inline_id_is_rejected(self) -> None:
        errors = run(bundle({"a": ("get", {"parameters": [ID_INLINE]})}))
        self.assertEqual(len(errors), 1)
        self.assertIn("a", errors[0])

    def test_inline_id_at_path_level_is_rejected(self) -> None:
        data = bundle({"a": ("get", {})})
        data["paths"]["/a"]["parameters"] = [ID_INLINE]
        self.assertEqual(len(run(data)), 1)

    def test_other_inline_names_and_locations_are_accepted(self) -> None:
        other = {"name": "widgetId", "in": "path", "required": True, "schema": {"type": "string"}}
        query = {"name": "id", "in": "query", "schema": {"type": "string"}}
        self.assertEqual(run(bundle({"a": ("get", {"parameters": [other, query]})})), [])

    def test_allowlisted_inline_id_is_accepted(self) -> None:
        data = bundle({"a": ("get", {"parameters": [ID_INLINE]})})
        self.assertEqual(run(data, ids={"a": "reason"}), [])

    def test_stale_allowlist_entry_is_rejected(self) -> None:
        data = bundle({"a": ("get", {"parameters": [ID_REF]})})
        errors = run(data, ids={"a": "reason", "gone": "reason"})
        self.assertEqual(len(errors), 2)
        self.assertTrue(all("stale allowlist entry" in error for error in errors))


class StringLimitTests(unittest.TestCase):
    def one(self, schema: dict, method: str = "post", schemas: dict | None = None) -> list[str]:
        return run(bundle({"a": (method, body(schema))}, schemas))

    def test_property_with_max_length_is_accepted(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string", "maxLength": 255}}}
        self.assertEqual(self.one(schema), [])

    def test_property_without_max_length_is_rejected(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string"}}}
        errors = self.one(schema)
        self.assertEqual(len(errors), 1)
        self.assertIn("op:a.name", errors[0])

    def test_methods_with_a_body_are_checked_and_others_are_not(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string"}}}
        for method in ("post", "put", "patch"):
            self.assertEqual(len(self.one(schema, method)), 1, method)
        self.assertEqual(self.one(schema, "get"), [])
        self.assertEqual(self.one(schema, "delete"), [])

    def test_exempt_properties_are_accepted(self) -> None:
        for exempt in ({"enum": ["a"]}, {"const": "a"}, {"format": "uuid"}, {"format": "date"},
                       {"format": "date-time"}, {"format": "uri"}, {"format": "email"}, {"format": "binary"}):
            schema = {"type": "object", "properties": {"x": {"type": "string", **exempt}}}
            self.assertEqual(self.one(schema), [], exempt)

    def test_other_formats_and_other_types(self) -> None:
        self.assertEqual(len(self.one({"type": "object", "properties": {"x": {"type": "string", "format": "password"}}})), 1)
        self.assertEqual(self.one({"type": "object", "properties": {"x": {"type": "integer"}, "y": {"type": "boolean"}}}), [])

    def test_nullable_string_without_limit_is_rejected(self) -> None:
        schema = {"type": "object", "properties": {"x": {"type": ["string", "null"]}}}
        self.assertEqual(len(self.one(schema)), 1)

    def test_property_reached_through_a_reference_is_rejected(self) -> None:
        schemas = {"Write": {"type": "object", "properties": {"name": {"type": "string"}}}}
        errors = self.one({"$ref": "#/components/schemas/Write"}, schemas=schemas)
        self.assertEqual(len(errors), 1)
        self.assertIn("Write.name", errors[0])

    def test_property_reached_through_all_of_is_rejected(self) -> None:
        schemas = {"Base": {"type": "object", "properties": {"name": {"type": "string"}}}}
        schema = {"allOf": [{"$ref": "#/components/schemas/Base"}, {"type": "object", "properties": {"b": {"type": "string", "maxLength": 5}}}]}
        errors = self.one(schema, schemas=schemas)
        self.assertEqual(len(errors), 1)
        self.assertIn("Base.name", errors[0])

    def test_property_reached_through_one_of_and_any_of_is_rejected(self) -> None:
        for combiner in ("oneOf", "anyOf"):
            schema = {combiner: [{"type": "object", "properties": {"name": {"type": "string"}}}]}
            self.assertEqual(len(self.one(schema)), 1, combiner)

    def test_property_reached_through_items_is_rejected(self) -> None:
        schema = {"type": "object", "properties": {"tags": {"type": "array", "items": {"type": "string"}}}}
        errors = self.one(schema)
        self.assertEqual(len(errors), 1)
        self.assertIn("tags[]", errors[0])

    def test_items_and_additional_properties_with_a_limit_are_accepted(self) -> None:
        schema = {"type": "object", "properties": {
            "tags": {"type": "array", "items": {"type": "string", "maxLength": 10}},
            "labels": {"type": "object", "additionalProperties": {"type": "string", "maxLength": 10}}}}
        self.assertEqual(self.one(schema), [])

    def test_additional_properties_without_limit_is_rejected(self) -> None:
        schema = {"type": "object", "additionalProperties": {"type": "string"}}
        self.assertEqual(len(self.one(schema)), 1)

    def test_recursive_schema_terminates(self) -> None:
        schemas = {"Node": {"type": "object", "properties": {"child": {"$ref": "#/components/schemas/Node"},
                                                              "name": {"type": "string", "maxLength": 5}}}}
        self.assertEqual(self.one({"$ref": "#/components/schemas/Node"}, schemas=schemas), [])

    def test_response_schemas_are_not_checked(self) -> None:
        operation = {"responses": {"200": {"content": {"application/json": {"schema": {
            "type": "object", "properties": {"name": {"type": "string"}}}}}}}}
        self.assertEqual(run(bundle({"a": ("post", operation)})), [])

    def test_allowlisted_gap_is_accepted(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string"}}}
        data = bundle({"a": ("post", body(schema))})
        self.assertEqual(run(data, gaps={"op:a.name": "reason"}), [])

    def test_stale_allowlist_entry_is_rejected(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string", "maxLength": 5}}}
        data = bundle({"a": ("post", body(schema))})
        errors = run(data, gaps={"op:a.name": "reason"})
        self.assertEqual(len(errors), 1)
        self.assertIn("stale allowlist entry", errors[0])

    def test_new_property_next_to_an_allowlisted_one_is_rejected(self) -> None:
        schema = {"type": "object", "properties": {"name": {"type": "string"}, "added": {"type": "string"}}}
        data = bundle({"a": ("post", body(schema))})
        errors = run(data, gaps={"op:a.name": "reason"})
        self.assertEqual(len(errors), 1)
        self.assertIn("op:a.added", errors[0])


class EnvelopeTests(unittest.TestCase):
    def schemas(self, **named: dict) -> dict:
        return bundle(schemas=named)

    def test_items_is_accepted(self) -> None:
        data = self.schemas(Good=page(items={"type": "array", "items": {"type": "string"}}))
        self.assertEqual(run(data), [])

    def test_other_name_is_rejected(self) -> None:
        data = self.schemas(Bad=page(rows={"type": "array", "items": {"type": "string"}}))
        errors = run(data)
        self.assertEqual(len(errors), 1)
        self.assertIn("Bad", errors[0])

    def test_items_that_is_no_array_is_rejected(self) -> None:
        self.assertEqual(len(run(self.schemas(Bad=page(items={"type": "string"})))), 1)

    def test_further_arrays_next_to_items_are_accepted(self) -> None:
        data = self.schemas(Good=page(items={"type": "array"}, actors={"type": "array"}))
        self.assertEqual(run(data), [])

    def test_schema_without_the_paging_member_is_accepted(self) -> None:
        data = self.schemas(Plain={"type": "object", "properties": {"rows": {"type": "array"}}})
        self.assertEqual(run(data), [])

    def test_allowlisted_envelope_is_accepted(self) -> None:
        data = self.schemas(Old=page(rows={"type": "array"}))
        self.assertEqual(run(data, envelopes={"Old": "reason"}), [])

    def test_stale_allowlist_entry_is_rejected(self) -> None:
        data = self.schemas(Old=page(items={"type": "array"}))
        errors = run(data, envelopes={"Old": "reason"})
        self.assertEqual(len(errors), 1)
        self.assertIn("stale allowlist entry", errors[0])


class ContractTests(unittest.TestCase):
    def main(self, data: dict) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bundle.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = check.main(["check", str(path)])
        return code, out.getvalue()

    def test_every_allowlist_entry_has_a_reason(self) -> None:
        for allowlist in (check.ID_PARAMETER_ALLOWLIST, check.STRING_LIMIT_ALLOWLIST, check.ENVELOPE_ALLOWLIST):
            self.assertTrue(all(reason.strip() for reason in allowlist.values()))

    def test_violation_fails_the_script(self) -> None:
        code, output = self.main(bundle({"a": ("get", {"parameters": [ID_INLINE]})}))
        self.assertEqual(code, 1)
        self.assertIn("violations", output)

    def test_missing_argument_is_a_usage_error(self) -> None:
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(check.main(["check"]), 2)


if __name__ == "__main__":
    unittest.main()
