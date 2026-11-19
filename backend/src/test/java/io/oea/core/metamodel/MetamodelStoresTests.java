package io.oea.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.metamodel.MetamodelConfigurationStore.Versioned;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The stores of {@code core.metamodel} that moved out of {@code app.metamodel} (change
 * s17-sql-into-core-metamodel) against a migrated database on the runtime role: the queries that
 * the service tests reach only through the whole application, namely dynamic SQL, locks and
 * lookups of absent rows.
 */
class MetamodelStoresTests {

	private static final UUID INSTANCE = MetamodelConfigurationStore.INSTANCE;

	private static final Instant NOW = Instant.parse("2026-11-19T09:00:00Z");

	private static OeaDatabase postgres;

	private static JdbcTemplate owner;

	private static JdbcTemplate app;

	private static long person;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		owner = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true));
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		person = owner.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id", Long.class,
				"kurt-" + UUID.randomUUID() + "@example.org");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void configurationStoreFindsLocksAndVersions() {
		MetamodelConfigurationStore store = new MetamodelConfigurationStore(app);
		UUID metatype = new MetatypeStore(app).ids().getFirst();

		assertThat(store.exists(INSTANCE)).isTrue();
		assertThat(store.exists(UUID.randomUUID())).isFalse();
		assertThat(store.find(INSTANCE)).get().extracting(MetamodelConfigurationStore.ConfigurationRow::scope).isEqualTo("instance");
		assertThat(store.find(UUID.randomUUID())).isEmpty();
		assertThat(store.list().getFirst().id()).isEqualTo(INSTANCE);
		store.lock(INSTANCE);
		assertThat(store.solutionExists(UUID.randomUUID())).isFalse();
		assertThat(store.nameTaken(store.find(INSTANCE).orElseThrow().name().toUpperCase(), null)).isTrue();
		assertThat(store.nameTaken(store.find(INSTANCE).orElseThrow().name(), INSTANCE)).isFalse();
		assertThat(store.reimportPolicy(INSTANCE)).isIn("keep", "remove");
		store.setReimportPolicy(INSTANCE, "remove");
		assertThat(store.reimportPolicy(INSTANCE)).isEqualTo("remove");
		store.setReimportPolicy(INSTANCE, "keep");

		int before = store.version(Versioned.METATYPE, metatype).orElseThrow();
		store.incrementVersion(Versioned.METATYPE, metatype);
		assertThat(store.version(Versioned.METATYPE, metatype)).contains(before + 1);
		assertThat(store.version(Versioned.METATYPE, UUID.randomUUID())).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(Versioned.class)
	void everyVersionedTableAnswersAnUnknownIdWithNothing(Versioned element) {
		assertThat(new MetamodelConfigurationStore(app).version(element, UUID.randomUUID())).isEmpty();
	}

	@Test
	void retiredPropertyDefinitionHasNoVersion() {
		PropertyDefinitionStore definitions = new PropertyDefinitionStore(app);
		MetamodelConfigurationStore store = new MetamodelConfigurationStore(app);
		UUID id = UUID.randomUUID();
		definitions.insert(id, new PropertyDefinitionStore.Fields("Retirable", "string", null, 255, null, null, null),
				"retirable", person);
		assertThat(store.version(Versioned.PROPERTY_DEFINITION, id)).contains(1);

		definitions.retire(id, NOW);

		assertThat(store.version(Versioned.PROPERTY_DEFINITION, id)).isEmpty();
		assertThat(definitions.stored(id)).isEmpty();
		assertThat(definitions.properties()).anyMatch((row) -> row.id().equals(id) && row.retired());
	}

	@Test
	void metatypeStoreWritesAndChecksNames() {
		MetatypeStore store = new MetatypeStore(app);
		UUID id = UUID.randomUUID();
		UUID base = UUID.randomUUID();
		store.insert(base, INSTANCE, "Base kind", "BK", false, null, 1, person, null, false);
		store.insert(id, INSTANCE, "Own kind", null, false, null, 2, person, null, false);

		assertThat(store.nameTaken(true, INSTANCE, INSTANCE, "own KIND", null)).isTrue();
		assertThat(store.nameTaken(true, INSTANCE, INSTANCE, "own KIND", id)).isFalse();
		assertThat(store.nameTaken(false, INSTANCE, INSTANCE, "Unused name", null)).isFalse();
		assertThat(store.existsOfKind(id, false, INSTANCE, INSTANCE)).isTrue();
		assertThat(store.existsOfKind(id, true, INSTANCE, INSTANCE)).isFalse();
		assertThat(store.existsOfKind(id, false)).isTrue();
		assertThat(store.existsOfKind(id, true)).isFalse();
		assertThat(store.existsIn(id, INSTANCE, UUID.randomUUID())).isTrue();
		assertThat(store.existsIn(UUID.randomUUID(), INSTANCE, INSTANCE)).isFalse();
		assertThat(store.builtIn(id)).isFalse();
		assertThat(store.builtIn(BuiltInMetatypes.STRUCTURING_AID)).isTrue();
		assertThat(store.isConnection(id)).contains(false);
		assertThat(store.isConnection(UUID.randomUUID())).isEmpty();
		assertThat(store.configOf(id)).contains(INSTANCE);
		assertThat(store.configOf(UUID.randomUUID())).isEmpty();
		assertThat(store.stored(id)).get().extracting(MetatypeStore.StoredRow::scope).isEqualTo("organization");

		assertThat(store.setBase(id, base)).isEqualTo(1);
		assertThat(store.setBase(id, base)).isZero();
		assertThat(store.bases()).containsEntry(id, base);
		assertThat(store.usage(base).subtypes()).isEqualTo(1);
		assertThat(store.usage(id)).isEqualTo(new MetatypeStore.Usage(0, 0, 0, 0, 0, 0));

		assertThat(store.reimportPolicy(id)).contains("inherit");
		store.setReimportPolicy(id, "remove");
		assertThat(store.reimportPolicy(id)).contains("remove");
		assertThat(store.showParallelChanges(id)).isPresent();
		store.setShowParallelChanges(id, false);
		assertThat(store.showParallelChanges(id)).contains(false);
		assertThat(store.showParallelChanges(UUID.randomUUID())).isEmpty();
		assertThat(store.reimportPolicy(UUID.randomUUID())).isEmpty();
		assertThat(store.policies()).anyMatch((row) -> row.id().equals(id) && "remove".equals(row.policy()));
		assertThat(store.kinds()).anyMatch((row) -> row.id().equals(id));
		assertThat(store.names()).containsEntry(id, "Own kind");
		assertThat(store.inConfigurations(INSTANCE, INSTANCE)).anyMatch((row) -> row.id().equals(id));
		assertThat(store.owners(id, "Other name")).extracting(ContentOwner::scope).containsExactly("organization");

		store.update(id, "Renamed kind", "RK", null, 3, null, false);
		assertThat(store.stored(id)).get().extracting(MetatypeStore.StoredRow::name).isEqualTo("Renamed kind");
		store.delete(id);
		assertThat(store.stored(id)).isEmpty();
	}

	@Test
	void importedMetatypeIsInsertedAndUpdated() {
		MetatypeStore store = new MetatypeStore(app);
		UUID id = UUID.randomUUID();

		assertThat(store.importedFields(id)).isEmpty();
		store.insertImported(id, INSTANCE, "Imported kind", "IK", false, 5, "pkg", "1.0", false, null);
		assertThat(store.importedFields(id)).contains(new MetatypeStore.ImportedFields("Imported kind", "IK", false, 5));

		store.updateImported(id, "Imported kind 2", null, false, 6, "1.1", false, null);

		assertThat(store.importedFields(id)).contains(new MetatypeStore.ImportedFields("Imported kind 2", null, false, 6));
		assertThat(store.owners(id, "x")).extracting(ContentOwner::packageId).containsExactly("pkg");
	}

	@Test
	void connectionConstraintsAreReplacedPerMetatype() {
		MetatypeStore metatypes = new MetatypeStore(app);
		ConnectionConstraintStore store = new ConnectionConstraintStore(app);
		UUID connection = UUID.randomUUID();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		metatypes.insert(connection, INSTANCE, "Link", null, true, null, 1, person, null, false);
		metatypes.insert(first, INSTANCE, "End one", null, false, null, 2, person, null, false);
		metatypes.insert(second, INSTANCE, "End two", null, false, null, 3, person, null, false);

		assertThat(store.of(connection)).isEqualTo(Map.of("source", List.of(), "target", List.of()));
		store.replace(connection, List.of(first, second), List.of(second));
		assertThat(store.of(connection).get("source")).containsExactlyInAnyOrder(first, second);
		assertThat(store.of(connection).get("target")).containsExactly(second);
		assertThat(store.all()).filteredOn((row) -> row.metatypeId().equals(connection)).hasSize(3);
		assertThat(metatypes.usage(first).endpoints()).isEqualTo(1);
		assertThat(metatypes.usage(second).endpoints()).isEqualTo(2);

		store.replace(connection, List.of(), List.of(first));
		assertThat(store.of(connection)).isEqualTo(Map.of("source", List.of(), "target", List.of(first)));
		store.deleteOf(connection);
		assertThat(store.of(connection)).isEqualTo(Map.of("source", List.of(), "target", List.of()));
	}

	@Test
	void propertyDefinitionStoreWritesDefinitionsValuesAndCategories() {
		PropertyDefinitionStore store = new PropertyDefinitionStore(app);
		UUID category = UUID.randomUUID();
		UUID property = UUID.randomUUID();
		store.insertCategory(category, "Group", 1, "A group");
		store.insert(property, new PropertyDefinitionStore.Fields("Rating", "enum", category, null,
				new BigDecimal("1"), new BigDecimal("9"), "x"), "rating", person);

		assertThat(store.categoryExists(category)).isTrue();
		assertThat(store.categoryExists(UUID.randomUUID())).isFalse();
		assertThat(store.categoryNameTaken("GROUP", null)).isTrue();
		assertThat(store.categoryNameTaken("GROUP", category)).isFalse();
		assertThat(store.categoryInUse(category)).isTrue();
		assertThat(store.categoryScope(category)).contains("organization");
		assertThat(store.categoryScope(UUID.randomUUID())).isEmpty();
		assertThat(store.nameTaken("rating", null)).isTrue();
		assertThat(store.nameTaken("rating", property)).isFalse();
		assertThat(store.columnNameTaken("rating")).isTrue();
		assertThat(store.columnNameTaken("unused")).isFalse();
		assertThat(store.stored(property)).get().extracting(PropertyDefinitionStore.StoredRow::dataType).isEqualTo("enum");

		assertThat(store.nextEnumOrder(property)).isZero();
		UUID value = UUID.randomUUID();
		store.insertEnumValue(value, property, "high", 4);
		assertThat(store.nextEnumOrder(property)).isEqualTo(5);
		assertThat(store.enumValueExists(property, "high")).isTrue();
		assertThat(store.enumValueExists(property, "low")).isFalse();
		assertThat(store.enumValue(value, property)).contains("high");
		assertThat(store.enumValue(value, UUID.randomUUID())).isEmpty();
		assertThat(store.enumValues()).anyMatch((row) -> row.id().equals(value));
		assertThat(store.enumValueOwners(value, UUID.randomUUID())).extracting(ContentOwner::scope)
			.containsExactly("organization");
		store.deleteEnumValue(value);
		assertThat(store.enumValue(value, property)).isEmpty();

		store.update(property, new PropertyDefinitionStore.Fields("Rating 2", "string", null, 10, null, null, null));
		assertThat(store.stored(property)).get().extracting(PropertyDefinitionStore.StoredRow::name).isEqualTo("Rating 2");
		store.updateCategory(category, "Group 2", 2, null);
		assertThat(store.categories()).anyMatch((row) -> row.id().equals(category) && row.name().equals("Group 2"));
		assertThat(store.propertyOwners(property, "other", "other_column")).hasSize(1);
		assertThat(store.categoryOwners(category, "other")).hasSize(1);

		store.delete(property);
		store.deleteCategory(category);
		assertThat(store.stored(property)).isEmpty();
		assertThat(store.categoryExists(category)).isFalse();
	}

	@Test
	void propertyMappingStoreKeepsEntriesByDate() {
		MetatypeStore metatypes = new MetatypeStore(app);
		PropertyDefinitionStore definitions = new PropertyDefinitionStore(app);
		PropertyMappingStore store = new PropertyMappingStore(app);
		UUID metatype = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		UUID property = UUID.randomUUID();
		metatypes.insert(metatype, INSTANCE, "Mapped kind", null, false, null, 1, person, null, false);
		metatypes.insert(child, INSTANCE, "Mapped child", null, false, null, 2, person, metatype, false);
		definitions.insert(property, new PropertyDefinitionStore.Fields("Mapped property", "string", null, null, null,
				null, null), "mapped_property", person);
		LocalDate today = LocalDate.of(2026, 11, 19);
		UUID past = UUID.randomUUID();
		UUID future = UUID.randomUUID();

		assertThat(store.mapped(property)).isFalse();
		store.insert(past, metatype, property, "optional", today.minusDays(1), 7, null, person, NOW);
		store.insert(future, metatype, property, "mandatory", today.plusDays(1), 7,
				new PropertyMappingStore.DefaultValue("x", null, null, null, null), person, null);

		assertThat(store.mapped(property)).isTrue();
		assertThat(store.sortOrders(metatype, property)).containsExactly(7, 7);
		assertThat(store.entryExists(metatype, property, today.plusDays(1))).isTrue();
		assertThat(store.entryExists(metatype, property, today)).isFalse();
		assertThat(store.entries()).extracting(PropertyMappingStore.EntryRow::id).contains(past, future);
		assertThat(store.active(today).get(metatype)).containsEntry(property, "optional");
		assertThat(store.active(today.plusDays(1)).get(metatype)).containsEntry(property, "mandatory");
		assertThat(store.mappedMetatypeNames(today).get(property)).containsExactly("Mapped kind");
		assertThat(store.dueExists(today)).isFalse();
		assertThat(store.dueExists(today.plusDays(1))).isTrue();
		assertThat(store.due(today.plusDays(1))).extracting(PropertyMappingStore.DueRow::id).containsExactly(future);
		store.markApplied(future, NOW);
		assertThat(store.dueExists(today.plusDays(1))).isFalse();
		assertThat(store.find(past, metatype)).contains(new PropertyMappingStore.MappingRef(property, "organization"));
		assertThat(store.find(past, child)).isEmpty();
		assertThat(store.mappedOnAny(property, List.of(metatype), List.of())).isTrue();
		assertThat(store.mappedOnAny(property, List.of(child), List.of(child))).isFalse();
		assertThat(store.mappedTwice(List.of(metatype), List.of(metatype))).isTrue();
		assertThat(store.mappedTwice(List.of(child), List.of(child))).isFalse();
		assertThat(store.sharesPropertyWith(metatype, List.of(child))).isFalse();
		assertThat(store.owners(past, child)).extracting(ContentOwner::scope).containsExactly("organization");
		assertThat(store.signature(metatype)).isEmpty();

		assertThat(store.deleteOrganization(metatype)).extracting(PropertyMappingStore.RemovedRow::id)
			.containsExactlyInAnyOrder(past, future);
		assertThat(store.mapped(property)).isFalse();
	}

	@Test
	void importedMappingsKeepTheirAppliedStateAndYieldToOrganizationEntries() {
		MetatypeStore metatypes = new MetatypeStore(app);
		PropertyDefinitionStore definitions = new PropertyDefinitionStore(app);
		PropertyMappingStore store = new PropertyMappingStore(app);
		UUID metatype = UUID.randomUUID();
		UUID property = UUID.randomUUID();
		metatypes.insert(metatype, INSTANCE, "Imported-mapped kind", null, false, null, 1, person, null, false);
		definitions.insert(property, new PropertyDefinitionStore.Fields("Imported-mapped", "string", null, null, null,
				null, null), "imported_mapped", person);
		LocalDate date = LocalDate.of(2026, 11, 19);
		UUID organization = UUID.randomUUID();
		UUID imported = UUID.randomUUID();
		UUID later = UUID.randomUUID();
		store.insert(organization, metatype, property, "optional", date, 1, null, person, null);

		assertThat(store.insertImported(imported, metatype, property, "mandatory", date, 1, null)).isZero();
		assertThat(store.insertImported(later, metatype, property, "mandatory", date.plusDays(2), 1, NOW)).isEqualTo(1);
		assertThat(store.signature(metatype)).hasSize(1).first().asString().startsWith(later.toString());

		Map<UUID, Instant> applied = store.deleteImported(metatype);

		assertThat(applied).containsOnlyKeys(later).containsEntry(later, NOW);
		assertThat(store.signature(metatype)).isEmpty();
		assertThat(store.find(organization, metatype)).isPresent();
		store.delete(organization);
		assertThat(store.find(organization, metatype)).isEmpty();
		store.deleteOf(metatype);
	}

	@Test
	void importedCategoriesAndPropertiesAreUpsertedWithTheirValues() {
		PropertyDefinitionStore store = new PropertyDefinitionStore(app);
		UUID category = UUID.randomUUID();
		UUID property = UUID.randomUUID();
		UUID value = UUID.randomUUID();

		store.upsertImportedCategory(category, "Imported group", 1, null, "pkg-p", "1");
		store.upsertImportedCategory(category, "Imported group", 2, "Now described", "pkg-p", "2");
		store.upsertImportedProperty(property, "Imported rating", "imported_rating", "enum", category, null, null, null,
				null, null, "pkg-p", "1", List.of(new PropertyDefinitionStore.EnumRow(value, property, "a", 0)));

		assertThat(store.categories()).filteredOn((row) -> row.id().equals(category))
			.singleElement()
			.satisfies((row) -> assertThat(row.version()).isEqualTo(2));
		assertThat(store.categoryOwners(category, "Free name")).extracting(ContentOwner::packageId).containsExactly("pkg-p");
		assertThat(store.enumValues()).filteredOn((row) -> row.propertyDefId().equals(property)).hasSize(1);
		store.upsertImportedProperty(property, "Imported rating", "imported_rating", "enum", category, null, null, null,
				null, null, "pkg-p", "2", List.of());
		assertThat(store.enumValues()).noneMatch((row) -> row.propertyDefId().equals(property));
		assertThat(store.properties()).filteredOn((row) -> row.id().equals(property))
			.singleElement()
			.satisfies((row) -> assertThat(row.version()).isEqualTo(2));
	}

	@Test
	void labelStoreSetsAndRemovesOrganizationLabels() {
		MetamodelLabelStore store = new MetamodelLabelStore(app);
		PropertyDefinitionStore definitions = new PropertyDefinitionStore(app);
		UUID property = UUID.randomUUID();
		definitions.insert(property, new PropertyDefinitionStore.Fields("Label subject", "string", null, null, null,
				null, null), "label_subject", person);

		store.upsertOrganization("property_def", property, "de", "name", "Bezeichnung", NOW);
		store.upsertOrganization("property_def", property, "de", "name", "Neue Bezeichnung", NOW);
		store.insertImported("property_def", property, "en", "name", "Package label", "pkg-l", NOW);

		assertThat(store.propertyLabels("de", List.of(property))).containsEntry(property, "Neue Bezeichnung");
		assertThat(store.propertyLabels("en", List.of(property))).containsEntry(property, "Package label");
		assertThat(store.propertyLabels("", List.of(property))).containsEntry(property, "Label subject");
		assertThat(store.elements(INSTANCE, "de")).anyMatch((row) -> row.entityId().equals(property)
				&& "Neue Bezeichnung".equals(row.organizationText()));
		assertThat(store.deleteOrganization("property_def", property, "de", "name")).isEqualTo(1);
		assertThat(store.deleteOrganization("property_def", property, "de", "name")).isZero();
		store.deleteImported("pkg-l");
		assertThat(store.propertyLabels("en", List.of(property))).containsEntry(property, "Label subject");

		UUID orphan = UUID.randomUUID();
		store.upsertOrganization("property_def", orphan, "de", "name", "Waise", NOW);
		store.deleteOrphans();
		assertThat(store.elements(INSTANCE, "de")).noneMatch((row) -> row.entityId().equals(orphan));
	}

	@Test
	void packageImportsAreRecordedPerPackage() {
		PackageImportStore store = new PackageImportStore(app);

		assertThat(store.importedPackageIds()).isEmpty();
		store.record("pkg-i", "1", "Publisher", UUID.randomUUID(), NOW, person, null, 2, 0);
		store.record("pkg-i", "2", "Publisher", UUID.randomUUID(), NOW, person, null, 0, 2);

		assertThat(store.importedPackageIds()).containsExactly("pkg-i");
	}

	@Test
	void visibilityStoreKeepsRolesHistoryAndPersonMetatype() {
		VisibilityStore store = new VisibilityStore(app);
		MetatypeStore metatypes = new MetatypeStore(app);
		UUID metatype = UUID.randomUUID();
		metatypes.insert(metatype, INSTANCE, "Visible kind", null, false, null, 1, person, null, false);
		owner.update("INSERT INTO authorization_roles (id, name) VALUES ('store-role', 'Store role')");

		assertThat(store.activeRoleIds()).contains("store-role");
		assertThat(store.lockActiveRoleIds()).contains("store-role");
		assertThat(store.lockActiveRoleIdsSorted()).contains("store-role").isSorted();
		assertThat(store.hides("store-role", metatype)).isFalse();
		store.hide("store-role", metatype);
		assertThat(store.hides("store-role", metatype)).isTrue();
		assertThat(store.history()).contains(new VisibilityStore.HistoryRow("store-role", metatype, "hide"));
		assertThat(store.hidingRoles(metatype, List.of("store-role"))).isEqualTo(1);
		assertThat(store.hidingRoles(metatype, List.of("other-role"))).isZero();
		store.show("store-role", metatype);
		assertThat(store.hides("store-role", metatype)).isFalse();

		assertThat(store.overrideRoles()).isEmpty();
		store.setOverrideRoles(List.of("store-role"));
		assertThat(store.overrideRoles()).containsExactly("store-role");
		store.setOverrideRoles(List.of());
		assertThat(store.overrideRoles()).isEmpty();

		assertThat(store.personMetatype()).isEqualTo(new VisibilityStore.PersonMetatypeRow(null, null));
		store.setPersonMetatype(metatype);
		assertThat(store.personMetatype()).isEqualTo(new VisibilityStore.PersonMetatypeRow(metatype, "Visible kind"));
		assertThat(metatypes.usage(metatype).personMetatype()).isEqualTo(1);
		store.setPersonMetatype(null);
	}

	@Test
	void propertyVisibilityIsSavedWithItsRoles() {
		VisibilityStore store = new VisibilityStore(app);
		PropertyDefinitionStore definitions = new PropertyDefinitionStore(app);
		MetatypeStore metatypes = new MetatypeStore(app);
		UUID property = UUID.randomUUID();
		UUID scoping = UUID.randomUUID();
		definitions.insert(property, new PropertyDefinitionStore.Fields("Guarded", "string", null, null, null, null,
				null), "guarded", person);
		metatypes.insert(scoping, INSTANCE, "Scoping link", null, true, null, 1, person, null, false);
		owner.update("INSERT INTO authorization_roles (id, name) VALUES ('guard-role', 'Guard role')");

		assertThat(store.isScopingType(List.of(scoping))).isFalse();
		store.save(property, "connection-scoped", scoping, List.of(), List.of());
		assertThat(store.isScopingType(List.of(scoping))).isTrue();
		assertThat(store.isScopingType(List.of(UUID.randomUUID()))).isFalse();
		assertThat(store.modes()).contains(new VisibilityStore.ModeRow(property, "connection-scoped", scoping));
		assertThat(metatypes.usage(scoping).scopingTypes()).isEqualTo(1);
		assertThat(metatypes.connectionIds()).contains(scoping);

		store.save(property, "role-restricted", null, List.of("guard-role"), List.of("guard-role"));
		assertThat(store.roleAccess()).contains(new VisibilityStore.RoleAccessRow(property, "guard-role", "read"),
				new VisibilityStore.RoleAccessRow(property, "guard-role", "write"));
		assertThat(store.activeProperties()).contains(new VisibilityStore.PropertyName(property, "Guarded"));
		assertThat(store.propertyName(property)).isEqualTo("Guarded");
		assertThat(store.retiredPropertyIds()).doesNotContain(property);
		definitions.retire(property, NOW);
		assertThat(store.retiredPropertyIds()).contains(property);
		assertThat(store.activeProperties()).doesNotContain(new VisibilityStore.PropertyName(property, "Guarded"));
	}

	@Test
	void changeStoreAcceptsEveryFilterOnAnEmptyTrail() {
		MetamodelChangeStore store = new MetamodelChangeStore(app);
		MetamodelChangeStore.Filter none = new MetamodelChangeStore.Filter(null, false, null, null, null, null, null);
		MetamodelChangeStore.Filter all = new MetamodelChangeStore.Filter(
				List.of("metamodel.metatype-%", "metamodel.metatype-imported"), true, person, UUID.randomUUID(), NOW,
				NOW.plusSeconds(86400), UUID.randomUUID().toString());

		for (MetamodelChangeStore.Filter filter : List.of(none, all)) {
			assertThat(store.count(filter)).isZero();
			assertThat(store.page(filter, 25, 0)).isEmpty();
		}
		assertThat(store.actors()).isEmpty();
	}

}
