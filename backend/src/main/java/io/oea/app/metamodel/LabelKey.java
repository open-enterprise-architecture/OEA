package io.oea.app.metamodel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Key of a translatable label, {@code {elementType}.{uuid}.{field}} (ADR-025, design D1 of
 * us-140-dynamic-labels): {@code metatype.{id}.name}, {@code property-category.{id}.name},
 * {@code property-def.{id}.label} and {@code enum-value.{id}.label}.
 */
public record LabelKey(Element element, UUID id) {

	/** Locales of the labels. */
	public static final Set<String> LOCALES = Set.of("de", "en", "fr", "it");

	/** Maximum length of a label text. */
	public static final int MAXIMUM_LENGTH = 200;

	/** An element type with its key prefix, its column value in {@code i18n_entries} and its field. */
	public enum Element {

		METATYPE("metatype", "metatype", "name"),
		PROPERTY_CATEGORY("property-category", "property_category", "name"),
		PROPERTY_DEF("property-def", "property_def", "label"),
		ENUM_VALUE("enum-value", "enum_value", "label");

		private final String prefix;

		private final String column;

		private final String field;

		Element(String prefix, String column, String field) {
			this.prefix = prefix;
			this.column = column;
			this.field = field;
		}

		public String prefix() {
			return this.prefix;
		}

		String column() {
			return this.column;
		}

		String field() {
			return this.field;
		}

		static Element ofColumn(String column) {
			for (Element element : values()) {
				if (element.column.equals(column)) {
					return element;
				}
			}
			throw new IllegalArgumentException(column);
		}

	}

	/** The key, or empty if the text is no well-formed label key. */
	public static Optional<LabelKey> parse(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (Element element : Element.values()) {
			String start = element.prefix + ".";
			String end = "." + element.field;
			if (key.startsWith(start) && key.endsWith(end) && key.length() == start.length() + 36 + end.length()) {
				try {
					UUID id = UUID.fromString(key.substring(start.length(), start.length() + 36));
					if (id.toString().equals(key.substring(start.length(), start.length() + 36))) {
						return Optional.of(new LabelKey(element, id));
					}
				}
				catch (IllegalArgumentException ex) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return this.element.prefix + "." + this.id + "." + this.element.field;
	}

}
