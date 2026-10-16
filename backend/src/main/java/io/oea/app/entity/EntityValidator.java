package io.oea.app.entity;

import io.oea.core.entity.EntityStore.Fields;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.TypedValue;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validation of an entity against the metamodel (design D2; REQ-149, ADR-022): system fields,
 * conversion of property values by data type, necessity of the active mappings and defaults
 * on create. Merges the input with the stored state (design D3).
 */
public final class EntityValidator {

	static final int NAME_MAX_LENGTH = 255;

	static final int DESCRIPTION_MAX_LENGTH = 2000;

	private EntityValidator() {
	}

	/** Whether an active entity with this ID and metatype exists. */
	@FunctionalInterface
	public interface References {

		boolean isActive(int entityId, UUID metatypeId);

	}

	/**
	 * The outcome: errors and warnings, and if valid, the resulting system fields and the
	 * complete set of property values after the write.
	 */
	public record Checked(List<Issue> errors, List<Issue> warnings, MetatypeRules.Metatype metatype, Fields fields,
			Map<UUID, TypedValue> values) {

		public boolean valid() {
			return this.errors.isEmpty();
		}

	}

	/**
	 * Checks an input against the rules.
	 *
	 * @param existing the stored entity on update, {@code null} on create
	 * @param stored its stored property values (empty on create)
	 */
	public static Checked check(EntityInput input, MetatypeRules rules, StoredEntity existing,
			Map<UUID, TypedValue> stored, References references) {
		List<Issue> errors = new ArrayList<>();
		List<Issue> warnings = new ArrayList<>();
		String name = input.name();
		if (name == null || name.isBlank()) {
			errors.add(new Issue("name", "REQUIRED", "The name must not be blank."));
		}
		else if (name.length() > NAME_MAX_LENGTH) {
			errors.add(new Issue("name", "INVALID_VALUE",
					"The name must not be longer than " + NAME_MAX_LENGTH + " characters."));
		}
		if (input.description() != null && input.description().length() > DESCRIPTION_MAX_LENGTH) {
			errors.add(new Issue("description", "INVALID_VALUE",
					"The description must not be longer than " + DESCRIPTION_MAX_LENGTH + " characters."));
		}
		if (input.parentId() != null) {
			errors.add(new Issue("parentId", "NOT_SUPPORTED", "Hierarchies are not supported yet (ADR-021)."));
		}
		if (input.lifecycle() != null) {
			errors.add(new Issue("lifecycle", "NOT_SUPPORTED", "The lifecycle is not supported yet."));
		}
		MetatypeRules.Metatype metatype = rules.metatype(input.metatypeId());
		if (metatype == null) {
			errors.add(new Issue("metatypeId", "METATYPE_NOT_FOUND", "The metatype does not exist."));
			return new Checked(errors, warnings, null, null, Map.of());
		}
		if (metatype.connection()) {
			errors.add(new Issue("metatypeId", "METATYPE_IS_CONNECTION",
					"The metatype is a connection type; connections are written as connections."));
			return new Checked(errors, warnings, metatype, null, Map.of());
		}
		if (existing != null && !existing.metatypeId().equals(metatype.id())) {
			errors.add(new Issue("metatypeId", "METATYPE_CHANGE_NOT_SUPPORTED",
					"The metatype of an existing entity cannot be changed."));
			return new Checked(errors, warnings, metatype, null, Map.of());
		}

		Map<UUID, TypedValue> values = new HashMap<>(stored);
		if (existing == null) {
			metatype.mappings().forEach((definition, mapping) -> {
				if (mapping.defaultValue() != null) {
					values.put(definition, mapping.defaultValue());
				}
			});
		}
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < input.properties().size(); i++) {
			EntityInput.PropertyInput property = input.properties().get(i);
			String field = "properties[" + i + "]";
			MetatypeRules.Mapping mapping = metatype.mappings().get(property.propertyDefId());
			if (mapping == null) {
				// REQ-149: a property without active mapping is ignored
				warnings.add(new Issue(field + ".propertyDefId", "PROPERTY_NOT_MAPPED",
						"The property is not mapped to the metatype and was ignored."));
				continue;
			}
			if (!seen.add(property.propertyDefId())) {
				errors.add(new Issue(field + ".propertyDefId", "INVALID_VALUE", "The property is listed twice."));
				continue;
			}
			if (!"public".equals(mapping.definition().visibilityMode())) {
				errors.add(new Issue(field + ".propertyDefId", "RESTRICTED_PROPERTY_NOT_SUPPORTED",
						"Properties with restricted visibility cannot be written yet."));
				continue;
			}
			if (property.value() == null || property.value().isEmpty()) {
				values.remove(property.propertyDefId());
				continue;
			}
			try {
				values.put(property.propertyDefId(), convert(mapping.definition(), property.value(), references));
			}
			catch (InvalidValueException ex) {
				errors.add(new Issue(field + ".value", "INVALID_VALUE", ex.getMessage()));
			}
		}
		for (MetatypeRules.Mapping mapping : metatype.mappings().values()) {
			if (values.containsKey(mapping.definition().id())) {
				continue;
			}
			if ("mandatory".equals(mapping.necessity())) {
				errors.add(new Issue("properties", "MANDATORY_MISSING",
						"The mandatory property " + mapping.definition().name() + " has no value."));
			}
			else if ("warning".equals(mapping.necessity())) {
				warnings.add(new Issue("properties[" + mapping.definition().name() + "]", "VALUE_MISSING",
						"The property " + mapping.definition().name() + " should have a value."));
			}
		}
		String description = (input.description() != null) ? input.description()
				: (existing != null) ? existing.description() : "";
		boolean logical = (input.logical() != null) ? input.logical() : (existing == null || existing.logical());
		Fields fields = new Fields(metatype.id(), name, description, logical,
				(existing != null) ? existing.externalId() : input.externalId());
		return new Checked(errors, warnings, metatype, fields, values);
	}

	private static TypedValue convert(MetatypeRules.Definition definition, String value, References references) {
		return switch (definition.dataType()) {
			case "string", "text", "url" -> text(definition, value);
			case "integer" -> integer(definition, value);
			case "boolean" -> switch (value) {
				case "true" -> TypedValue.ofBoolean(true);
				case "false" -> TypedValue.ofBoolean(false);
				default -> throw new InvalidValueException("The value must be true or false.");
			};
			case "date" -> {
				try {
					yield TypedValue.ofDate(LocalDate.parse(value));
				}
				catch (DateTimeParseException ex) {
					throw new InvalidValueException("The value must be a date (YYYY-MM-DD).");
				}
			}
			case "enum" -> {
				if (!definition.enumValues().contains(value)) {
					throw new InvalidValueException("The value is not one of the values of " + definition.name() + ".");
				}
				yield TypedValue.ofEnum(value);
			}
			case "reference" -> {
				int entityId;
				try {
					entityId = Integer.parseInt(value);
				}
				catch (NumberFormatException ex) {
					throw new InvalidValueException("The value must be an entity ID.");
				}
				if (!references.isActive(entityId, definition.refMetatypeId())) {
					throw new InvalidValueException("The referenced entity does not exist or has another metatype.");
				}
				yield TypedValue.ofReference(entityId);
			}
			default -> throw new InvalidValueException("The data type " + definition.dataType() + " is not supported.");
		};
	}

	private static TypedValue text(MetatypeRules.Definition definition, String value) {
		if (definition.maxLength() != null && value.length() > definition.maxLength()) {
			throw new InvalidValueException(
					"The value must not be longer than " + definition.maxLength() + " characters.");
		}
		if (definition.pattern() != null && !matches(definition.pattern(), value)) {
			throw new InvalidValueException("The value does not match the pattern of " + definition.name() + ".");
		}
		if ("url".equals(definition.dataType()) && !isWebUrl(value)) {
			throw new InvalidValueException("The value must be an absolute http or https URL.");
		}
		return TypedValue.ofText(value);
	}

	private static TypedValue integer(MetatypeRules.Definition definition, String value) {
		long number;
		try {
			number = Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new InvalidValueException("The value must be an integer.");
		}
		BigDecimal decimal = BigDecimal.valueOf(number);
		if (definition.minValue() != null && decimal.compareTo(definition.minValue()) < 0) {
			throw new InvalidValueException("The value must be at least " + definition.minValue().toPlainString() + ".");
		}
		if (definition.maxValue() != null && decimal.compareTo(definition.maxValue()) > 0) {
			throw new InvalidValueException("The value must be at most " + definition.maxValue().toPlainString() + ".");
		}
		return TypedValue.ofInteger(number);
	}

	private static boolean matches(String pattern, String value) {
		try {
			return Pattern.compile(pattern).matcher(value).matches();
		}
		catch (PatternSyntaxException ex) {
			throw new InvalidValueException("The pattern of the property is invalid.");
		}
	}

	private static boolean isWebUrl(String value) {
		try {
			URI uri = new URI(value);
			return uri.isAbsolute() && uri.getHost() != null
					&& ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

	private static final class InvalidValueException extends RuntimeException {

		InvalidValueException(String message) {
			super(message, null, false, false);
		}

	}

}
