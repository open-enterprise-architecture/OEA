package io.oea.core.entity;

import java.time.LocalDate;

/**
 * A property value in its typed column (ADR-022): exactly one component is set. Text-like
 * data types ({@code string}, {@code text}, {@code url}) use {@code text}.
 */
public record TypedValue(String text, Long integer, Boolean bool, LocalDate date, Integer reference,
		String enumValue) {

	public static TypedValue ofText(String value) {
		return new TypedValue(value, null, null, null, null, null);
	}

	public static TypedValue ofInteger(long value) {
		return new TypedValue(null, value, null, null, null, null);
	}

	public static TypedValue ofBoolean(boolean value) {
		return new TypedValue(null, null, value, null, null, null);
	}

	public static TypedValue ofDate(LocalDate value) {
		return new TypedValue(null, null, null, value, null, null);
	}

	public static TypedValue ofReference(int entityId) {
		return new TypedValue(null, null, null, null, entityId, null);
	}

	public static TypedValue ofEnum(String value) {
		return new TypedValue(null, null, null, null, null, value);
	}

	/** The value serialized as text, as written by clients (PropertyWriteValue). */
	public String asText() {
		if (this.text != null) {
			return this.text;
		}
		if (this.integer != null) {
			return this.integer.toString();
		}
		if (this.bool != null) {
			return this.bool.toString();
		}
		if (this.date != null) {
			return this.date.toString();
		}
		if (this.reference != null) {
			return this.reference.toString();
		}
		return this.enumValue;
	}

}
