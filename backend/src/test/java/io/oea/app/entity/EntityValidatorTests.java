package io.oea.app.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.entity.EntityInput.PropertyInput;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.TypedValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/** Spec repository/entity-write: validation per data type, constraint and necessity (design D2). */
class EntityValidatorTests {

	private static final UUID COMPONENT = UUID.randomUUID();

	private static final UUID FLOW = UUID.randomUUID();

	private static final UUID STATUS = UUID.randomUUID();

	private static final UUID OWNER = UUID.randomUUID();

	private static final UUID VERSION = UUID.randomUUID();

	private static final UUID COST = UUID.randomUUID();

	private static final UUID CRITICAL = UUID.randomUUID();

	private static final UUID LAUNCH = UUID.randomUUID();

	private static final UUID HOMEPAGE = UUID.randomUUID();

	private static final UUID HOST = UUID.randomUUID();

	private static final UUID SECRET = UUID.randomUUID();

	private static final UUID UNMAPPED = UUID.randomUUID();

	private static final UUID SERVER = UUID.randomUUID();

	private static final MetatypeRules RULES = rules();

	private static final EntityValidator.References REFERENCES = (id, metatype) -> id == 7 && SERVER.equals(metatype);

	@Test
	void unknownMetatype() {
		EntityValidator.Checked checked = check(new EntityInput("CRM", UUID.randomUUID(), null, null, null, null, null,
				List.of()));
		assertThat(checked.errors()).extracting(Issue::field, Issue::code)
			.containsExactly(Tuple.tuple("metatypeId", "METATYPE_NOT_FOUND"));
	}

	@Test
	void connectionTypesAreNotWrittenAsEntities() {
		assertThat(codes(check(input("Flow", FLOW)))).containsExactly("METATYPE_IS_CONNECTION");
	}

	@Test
	void metatypeChangedOnUpdate() {
		StoredEntity existing = new StoredEntity(1, SERVER, "CRM", "", true, 1, "x:1", false);
		EntityValidator.Checked checked = EntityValidator.check(input("CRM", COMPONENT), RULES, existing, Map.of(),
				REFERENCES);
		assertThat(codes(checked)).containsExactly("METATYPE_CHANGE_NOT_SUPPORTED");
	}

	@Test
	void namesAndDescriptions() {
		assertThat(codes(check(input(" ", COMPONENT)))).containsExactly("REQUIRED");
		assertThat(codes(check(input("x".repeat(256), COMPONENT)))).containsExactly("INVALID_VALUE");
		assertThat(codes(check(new EntityInput("CRM", COMPONENT, null, "d".repeat(2001), null, null, null,
				List.of(value(VERSION, "1")))))).containsExactly("INVALID_VALUE");
		assertThat(check(input("x".repeat(255), COMPONENT)).valid()).isTrue();
	}

	@Test
	void mandatoryPropertyMissing() {
		EntityValidator.Checked checked = check(new EntityInput("CRM", COMPONENT, null, null, null, null, null,
				List.of()));
		assertThat(checked.errors()).singleElement().satisfies((error) -> {
			assertThat(error.field()).isEqualTo("properties");
			assertThat(error.code()).isEqualTo("MANDATORY_MISSING");
		});
	}

	@Test
	void propertyWithNecessityWarningMissing() {
		EntityValidator.Checked checked = check(input("CRM", COMPONENT));
		assertThat(checked.valid()).isTrue();
		assertThat(checked.warnings()).extracting(Issue::field).containsExactly("properties[Owner]");
	}

	@Test
	void valueOutsideTheEnumValues() {
		EntityValidator.Checked checked = check(input("CRM", COMPONENT, value(STATUS, "unknown")));
		assertThat(checked.errors()).singleElement().satisfies((error) -> {
			assertThat(error.field()).isEqualTo("properties[1].value");
			assertThat(error.code()).isEqualTo("INVALID_VALUE");
		});
		assertThat(check(input("CRM", COMPONENT, value(STATUS, "active"))).values()).containsEntry(STATUS,
				TypedValue.ofEnum("active"));
	}

	@Test
	void propertyNotMappedToTheMetatype() {
		EntityValidator.Checked checked = check(input("CRM", COMPONENT, value(UNMAPPED, "x")));
		assertThat(checked.valid()).isTrue();
		assertThat(checked.warnings()).extracting(Issue::code).contains("PROPERTY_NOT_MAPPED");
		assertThat(checked.values()).doesNotContainKey(UNMAPPED);
	}

	@Test
	void propertyWithRestrictedVisibility() {
		assertThat(codes(check(input("CRM", COMPONENT, value(SECRET, "x")))))
			.containsExactly("RESTRICTED_PROPERTY_NOT_SUPPORTED");
	}

	@Test
	void valuesAreConvertedByDataType() {
		EntityValidator.Checked checked = check(input("CRM", COMPONENT, value(COST, "42"), value(CRITICAL, "true"),
				value(LAUNCH, "2026-10-16"), value(HOMEPAGE, "https://crm.example.org"), value(HOST, "7"),
				value(OWNER, "Kurt")));
		assertThat(checked.errors()).isEmpty();
		assertThat(checked.warnings()).isEmpty();
		assertThat(checked.values()).containsEntry(COST, TypedValue.ofInteger(42))
			.containsEntry(CRITICAL, TypedValue.ofBoolean(true))
			.containsEntry(LAUNCH, TypedValue.ofDate(LocalDate.of(2026, 10, 16)))
			.containsEntry(HOMEPAGE, TypedValue.ofText("https://crm.example.org"))
			.containsEntry(HOST, TypedValue.ofReference(7))
			.containsEntry(OWNER, TypedValue.ofText("Kurt"));
	}

	@Test
	void invalidValuesPerDataType() {
		for (PropertyInput invalid : List.of(value(COST, "4.2"), value(COST, "0"), value(COST, "1001"),
				value(CRITICAL, "yes"), value(LAUNCH, "16.10.2026"), value(HOMEPAGE, "ftp://example.org"),
				value(HOMEPAGE, "no url"), value(HOST, "8"), value(HOST, "x"), value(OWNER, "o".repeat(21)),
				value(VERSION, "v1"))) {
			assertThat(codes(check(input("CRM", COMPONENT, invalid)))).as(invalid.value()).contains("INVALID_VALUE");
		}
	}

	@Test
	void defaultsApplyOnCreateOnly() {
		assertThat(check(input("CRM", COMPONENT)).values()).containsEntry(STATUS, TypedValue.ofEnum("planned"));
		StoredEntity existing = new StoredEntity(1, COMPONENT, "CRM", "", true, 3, "x:1", false);
		EntityValidator.Checked update = EntityValidator.check(input("CRM", COMPONENT), RULES, existing,
				Map.of(VERSION, TypedValue.ofText("1")), REFERENCES);
		assertThat(update.values()).doesNotContainKey(STATUS);
	}

	@Test
	void updatesKeepOmittedFieldsAndRemoveEmptyValues() {
		StoredEntity existing = new StoredEntity(1, COMPONENT, "CRM", "Customer data", false, 3, "x:1", false);
		Map<UUID, TypedValue> stored = Map.of(VERSION, TypedValue.ofText("1"), OWNER, TypedValue.ofText("Kurt"));
		EntityValidator.Checked checked = EntityValidator.check(
				new EntityInput("CRM 2", COMPONENT, "x:1", null, null, null, null, List.of(value(OWNER, ""))), RULES,
				existing, stored, REFERENCES);
		assertThat(checked.fields().description()).isEqualTo("Customer data");
		assertThat(checked.fields().logical()).isFalse();
		assertThat(checked.values()).containsOnlyKeys(VERSION);
	}

	@Test
	void hierarchyAndLifecycleAreNotSupportedYet() {
		assertThat(codes(check(new EntityInput("CRM", COMPONENT, null, null, null, "active", 3,
				List.of(value(VERSION, "1")))))).containsExactly("NOT_SUPPORTED", "NOT_SUPPORTED");
	}

	private static EntityValidator.Checked check(EntityInput input) {
		return EntityValidator.check(input, RULES, null, Map.of(), REFERENCES);
	}

	/** An input with the mandatory property Version set. */
	private static EntityInput input(String name, UUID metatype, PropertyInput... values) {
		List<PropertyInput> properties = new ArrayList<>();
		properties.add(value(VERSION, "1"));
		properties.addAll(List.of(values));
		return new EntityInput(name, metatype, null, null, null, null, null, properties);
	}

	private static PropertyInput value(UUID definition, String value) {
		return new PropertyInput(definition, value);
	}

	private static List<String> codes(EntityValidator.Checked checked) {
		return checked.errors().stream().map(Issue::code).toList();
	}

	private static MetatypeRules rules() {
		Map<UUID, MetatypeRules.Mapping> mappings = new LinkedHashMap<>();
		mapping(mappings, definition(VERSION, "Version", "string", null, "[0-9.]+"), "mandatory", null);
		mapping(mappings, new MetatypeRules.Definition(STATUS, "Status", "enum", null, null, null, null, "public", null,
				Set.of("planned", "active")), "optional", TypedValue.ofEnum("planned"));
		mapping(mappings, definition(OWNER, "Owner", "string", 20, null), "warning", null);
		mapping(mappings, new MetatypeRules.Definition(COST, "Cost", "integer", null, BigDecimal.ONE,
				BigDecimal.valueOf(1000), null, "public", null, Set.of()), "optional", null);
		mapping(mappings, definition(CRITICAL, "Critical", "boolean", null, null), "optional", null);
		mapping(mappings, definition(LAUNCH, "Launch", "date", null, null), "optional", null);
		mapping(mappings, definition(HOMEPAGE, "Homepage", "url", null, null), "optional", null);
		mapping(mappings, new MetatypeRules.Definition(HOST, "Host", "reference", null, null, null, null, "public",
				SERVER, Set.of()), "optional", null);
		mapping(mappings, new MetatypeRules.Definition(SECRET, "Secret", "string", null, null, null, null,
				"role-restricted", null, Set.of()), "optional", null);
		return MetatypeRules.of(new MetatypeRules.Metatype(COMPONENT, "Application Component", false, mappings),
				new MetatypeRules.Metatype(FLOW, "Flow", true, Map.of()),
				new MetatypeRules.Metatype(SERVER, "Server", false, Map.of()));
	}

	private static MetatypeRules.Definition definition(UUID id, String name, String dataType, Integer maxLength,
			String pattern) {
		return new MetatypeRules.Definition(id, name, dataType, maxLength, null, null, pattern, "public", null,
				Set.of());
	}

	private static void mapping(Map<UUID, MetatypeRules.Mapping> mappings, MetatypeRules.Definition definition,
			String necessity, TypedValue defaultValue) {
		mappings.put(definition.id(), new MetatypeRules.Mapping(definition, necessity, defaultValue));
	}

}
