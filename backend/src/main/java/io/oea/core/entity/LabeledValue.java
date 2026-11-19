package io.oea.core.entity;

/**
 * A typed value with the label of its reference target, for responses that show a reference by
 * name and metatype abbreviation. {@code value} is {@code null} for no value; the reference name
 * and abbreviation are {@code null} unless the value is a reference to an existing entity.
 */
public record LabeledValue(TypedValue value, String referenceName, String referenceAbbreviation) {
}
