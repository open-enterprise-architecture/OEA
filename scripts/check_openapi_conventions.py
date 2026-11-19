#!/usr/bin/env python3
"""Check the conventions of api/openapi.yaml that Redocly cannot express (S17 change 8, API-8).

The script reads a JSON bundle of the contract that keeps the ``$ref`` references:

    redocly bundle api/openapi.yaml --ext json -o openapi.bundle.json
    python3 scripts/check_openapi_conventions.py openapi.bundle.json

Fails (exit code 1) if
- a path parameter named ``id`` is declared inline instead of as a reference to a component
  parameter (the shared ``UuidIdParam``),
- a ``type: string`` property reachable from the request body of a POST, PUT or PATCH operation
  (following ``$ref``, ``allOf``, ``oneOf``, ``anyOf``, ``items`` and ``additionalProperties``)
  declares no ``maxLength``; properties with ``enum`` or ``const`` and with a ``format`` of
  ``uuid``, ``date``, ``date-time``, ``uri``, ``email`` or ``binary`` are exempt,
- a component schema with the paging member ``page`` (``PageMeta``) has no array property ``items``,
- an allowlist entry matches no violation any more (the element is gone or follows the rule now).
  The allowlists therefore can only shrink.

Existing violations are allowlisted with a reason; new operations and schemas follow the rules.
The script uses only the Python standard library.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

HTTP_METHODS = ("get", "put", "post", "delete", "patch")
BODY_METHODS = ("post", "put", "patch")
EXEMPT_FORMATS = {"uuid", "date", "date-time", "uri", "email", "binary"}

# Rule 1: planned operations whose path parameter `id` is not a UUID. The shared parameter
# is chosen when the operation is implemented.
ID_PARAMETER_ALLOWLIST = {
    "getShapePalette": "planned; the id is the viewpoint key (a slug), not a UUID",
    "updateShapePalette": "planned; the id is the viewpoint key (a slug), not a UUID",
    "getAuditEventChanges": "planned; the id is an integer sequence number, not a UUID",
}

# Rule 3: page envelopes that keep their list name. Renaming changes the client and gains nothing.
ENVELOPE_ALLOWLIST = {
    "CatalogQueryResult": "established before the rule; the list is named rows",
    "SolutionDiff": "established before the rule; the list is named entries",
    "EntityHistoryResponse": "established before the rule; the list is named versions",
    "ConformanceResult": "planned operation getConformanceResult; the list is named violations",
}

# Rule 2: baseline of request string properties without maxLength, keyed by
# "<component schema>.<property path>" ("[]" marks array items, "{}" additional properties) or
# "op:<operationId>.<property path>" for an inline request schema. Contract limits are enforced
# by bean validation before the handler: adding a maxLength to an existing property changes the
# error response of its operation or introduces a new limit, so aligning them is a follow-up.
STRING_LIMIT_BASELINE: dict[str, list[str]] = {
    "request of a planned operation only; the limit is set when the operation is implemented": [
        "AnalyticsQueryRequest.parameters[]",
        "SystemSettings.instanceName",
        "SystemSettings.licenseKey",
        "SystemSettings.minClientVersion",
        "SystemSettings.recommendedClientVersion",
        "TRMEntryWrite.category",
        "TRMEntryWrite.name",
        "TRMEntryWrite.notes",
        "TRMEntryWrite.version",
        "op:updateContinuumConfig.levels[].description",
        "op:updateContinuumConfig.levels[].name",
    ],
    "part of the metamodel package document, which the import validates and whose size the package "
    "limit bounds (ADR-026)": [
        "I18nBundle{}",
        "MetamodelPackage.formatVersion",
        "MetamodelPackage.packageId",
        "MetamodelPackage.sourceInstanceId",
        "MetatypeDetail.architectureLayerName",
        "MetatypePropertyMapping.defaultValue.valueEnum",
        "MetatypePropertyMapping.defaultValue.valueText",
        "MetatypePropertyMapping.snapshotEnumValues[]",
        "MetatypeSummary.abbreviation",
        "MetatypeSummary.name",
        "PropertyCategory.description",
        "PropertyCategory.name",
        "PropertyDefinitionDetail.label",
        "PropertyDefinitionDetail.name",
        "PropertyDefinitionDetail.pattern",
        "PropertyDefinitionDetail.sqlColumnName",
        "PropertyEnumValue.label",
        "PropertyEnumValue.value",
        "ShapeAssignment.borderColor",
        "ShapeAssignment.fillColor",
        "ShapeAssignment.iconKey",
        "ShapeAssignment.shapeKey",
        "ShapeAssignment.viewpointKey",
        "SourcePackage.packageId",
        "SourcePackage.version",
    ],
    "no contract limit; the operation answers an over-long value from its domain validation or stores it "
    "unbounded, so a limit changes its error response or adds a limit (follow-up)": [
        "AuthProviderWrite.claimMappings{}",
        "AuthProviderWrite.clientId",
        "AuthProviderWrite.clientSecret",
        "AuthProviderWrite.issuerUrl",
        "AuthProviderWrite.name",
        "AuthProviderWrite.scopes",
        "CatalogSort.column",
        "ConnectionWrite.externalId",
        "ConnectionWrite.sourceExternalId",
        "ConnectionWrite.targetExternalId",
        "DeltaConflictResolution.fields[].field",
        "DeltaConflictResolution.fields[].value",
        "EntityWrite.description",
        "EntityWrite.externalId",
        "EntityWrite.lifecycle",
        "EntityWrite.name",
        "InitialPassword.password",
        "LocalLoginTotpRequest.code",
        "MetatypePropertyMappingWrite.defaultValue.valueEnum",
        "MetatypePropertyMappingWrite.defaultValue.valueText",
        "MetatypeWrite.abbreviation",
        "MetatypeWrite.name",
        "PasswordChange.newPassword",
        "PersonMetatype.metatypeName",
        "PersonPreferences.uiLanguage",
        "PlateauGoLiveRequest.name",
        "PropertyCategoryWrite.description",
        "PropertyCategoryWrite.name",
        "PropertyDefinitionWrite.name",
        "PropertyDefinitionWrite.pattern",
        "PropertyVisibilityEntry.label",
        "PropertyVisibilityEntry.metatypes[]",
        "PropertyVisibilityEntry.propertyName",
        "PropertyWriteValue.value",
        "RestoreRequest.fields[]",
        "RoleId",
        "SetupRequest.instanceSlug",
        "SetupRequest.loginName",
        "SolutionOpenIssueResolution.resolutions[].value",
        "TotpConfirmation.code",
        "ViewpointMapping.visualHint",
        "op:createEnumValue.value",
    ],
}
STRING_LIMIT_ALLOWLIST: dict[str, str] = {key: reason for reason, keys in STRING_LIMIT_BASELINE.items()
                                          for key in keys}


def schema_node(bundle: dict, node: dict) -> dict:
    """Resolve a local reference to a component schema."""
    ref = node.get("$ref")
    if ref is None:
        return node
    target = bundle
    for part in ref.removeprefix("#/").split("/"):
        target = target[part]
    return target


def is_string(node: dict) -> bool:
    kind = node.get("type")
    return kind == "string" or (isinstance(kind, list) and "string" in kind)


def string_gaps(bundle: dict) -> set[str]:
    """Keys of all string properties reachable from request bodies that lack maxLength."""
    gaps: set[str] = set()
    visited: set[str] = set()

    def walk(node: dict, key: str) -> None:
        ref = node.get("$ref")
        if ref is not None:
            name = ref.rsplit("/", 1)[-1]
            if name in visited:
                return
            visited.add(name)
            walk(schema_node(bundle, node), name)
            return
        if is_string(node) and "maxLength" not in node and "enum" not in node and "const" not in node \
                and node.get("format") not in EXEMPT_FORMATS:
            gaps.add(key)
        for combiner in ("allOf", "oneOf", "anyOf"):
            for sub in node.get(combiner, []):
                walk(sub, key)
        for name, sub in node.get("properties", {}).items():
            walk(sub, f"{key}.{name}")
        if isinstance(node.get("items"), dict):
            walk(node["items"], f"{key}[]")
        if isinstance(node.get("additionalProperties"), dict):
            walk(node["additionalProperties"], f"{key}{{}}")

    for item in bundle.get("paths", {}).values():
        for method in BODY_METHODS:
            operation = item.get(method)
            if operation is None:
                continue
            for media in operation.get("requestBody", {}).get("content", {}).values():
                schema = media.get("schema")
                if schema is not None:
                    walk(schema, f"op:{operation['operationId']}")
    return gaps


def inline_id_parameters(bundle: dict) -> set[str]:
    """Operation ids (or path templates for path-level parameters) with an inline path parameter id."""
    found: set[str] = set()
    for path, item in bundle.get("paths", {}).items():
        for parameter in item.get("parameters", []):
            if parameter.get("in") == "path" and parameter.get("name") == "id":
                found.add(path)
        for method in HTTP_METHODS:
            operation = item.get(method)
            if operation is None:
                continue
            for parameter in operation.get("parameters", []):
                if parameter.get("in") == "path" and parameter.get("name") == "id":
                    found.add(operation["operationId"])
    return found


def envelopes_without_items(bundle: dict) -> set[str]:
    """Component schemas with the paging member `page` that have no array property `items`."""
    found: set[str] = set()
    for name, schema in bundle.get("components", {}).get("schemas", {}).items():
        properties = schema.get("properties", {})
        page = properties.get("page", {})
        if not page.get("$ref", "").endswith("/PageMeta"):
            continue
        if properties.get("items", {}).get("type") != "array":
            found.add(name)
    return found


def apply_allowlist(rule: str, found: set[str], allowlist: dict[str, str], errors: list[str]) -> None:
    for key in sorted(found - allowlist.keys()):
        errors.append(f"{rule}: {key}")
    for key in sorted(allowlist.keys() - found):
        errors.append(f"{rule}: stale allowlist entry {key} (it follows the rule or is gone; remove it)")


def check(bundle: dict, id_allowlist: dict[str, str], gap_allowlist: dict[str, str],
          envelope_allowlist: dict[str, str]) -> list[str]:
    errors: list[str] = []
    apply_allowlist("inline path parameter id, use the shared UuidIdParam", inline_id_parameters(bundle),
                    id_allowlist, errors)
    apply_allowlist("request string property without maxLength", string_gaps(bundle), gap_allowlist, errors)
    apply_allowlist("page envelope without an items array", envelopes_without_items(bundle), envelope_allowlist,
                    errors)
    return errors


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: check_openapi_conventions.py <openapi.bundle.json>", file=sys.stderr)
        return 2
    bundle = json.loads(Path(argv[1]).read_text(encoding="utf-8"))
    errors = check(bundle, ID_PARAMETER_ALLOWLIST, STRING_LIMIT_ALLOWLIST, ENVELOPE_ALLOWLIST)
    for error in errors:
        print(f"ERROR {error}")
    if errors:
        print(f"{len(errors)} violations")
        return 1
    print(f"OK conventions: {len(ID_PARAMETER_ALLOWLIST)} id parameters, {len(STRING_LIMIT_ALLOWLIST)} "
          f"string limits and {len(ENVELOPE_ALLOWLIST)} envelopes allowlisted")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
