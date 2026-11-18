package io.oea.app.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The navigation tree (us-054-catalog-navigation-tree): navigation/navigation-tree on an own
 * database. Kurt is an enterprise architect with {@code viewpoint:manage}, Franz a viewer. Every
 * test works in its own folders under the root.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NavigationTreeTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String TREE = "/api/v1/views/explorer";

	private static final String NODES = "/api/v1/tree-nodes";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestSession kurt;

	private TestSession franz;

	private long kurtId;

	private String root;

	private UUID application;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.franz = principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.root = this.jdbc.queryForObject("SELECT id::text FROM tree_nodes WHERE parent_id IS NULL", String.class);
		this.application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Application " + suffix());
	}

	// Read the tree

	@Test
	void theTreeStartsAtTheRoot() {
		JsonNode tree = ok(call(HttpMethod.GET, TREE, null, this.franz));
		assertThat(tree.path("id").asString()).isEqualTo(this.root);
		assertThat(tree.path("name").asString()).isEqualTo("Repository");
		assertThat(tree.path("parentId").isNull()).isTrue();
	}

	@Test
	void catalogUnderItsFolder() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		created(place(landscape, inventory, null));
		JsonNode folder = folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), landscape);
		JsonNode item = folder.path("items").get(0);
		assertThat(item.path("itemType").asString()).isEqualTo("catalog");
		assertThat(item.path("referenceId").asString()).isEqualTo(inventory);
		assertThat(item.path("name").asString()).startsWith("Application Inventory");
		assertThat(item.path("displayLabel").isNull()).isTrue();
		assertThat(item.path("scope").asString()).isEqualTo("instance");
	}

	@Test
	void anotherPersonsPersonalCatalog() {
		String landscape = newFolder("IT Landscape", this.root);
		String candidates = catalog("Kurt's Candidates", "personal", this.kurt);
		created(place(landscape, candidates, null));
		assertThat(folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), landscape).path("items").size()).isZero();
		JsonNode own = folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), landscape).path("items").get(0);
		assertThat(own.path("scope").asString()).isEqualTo("personal");
	}

	@Test
	void foldersAndEntriesComeInTheirManualOrderNotByName() {
		String parent = newFolder("Ordered", this.root);
		String beta = createdId(createFolder("beta", parent), "beta");
		createdId(createFolder("Alpha", parent), "Alpha");
		createdId(createFolder("Gamma", parent), "Gamma");
		created(place(parent, catalog("Zulu", "instance", this.kurt), null));
		created(place(parent, catalog("Mike", "instance", this.kurt), "alpha label"));
		JsonNode folder = folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), parent);
		assertThat(texts(folder.path("children"), "name")).containsExactly("beta", "Alpha", "Gamma");
		assertThat(texts(folder.path("children"), "sortOrder")).containsExactly("0", "1", "2");
		assertThat(labels(folder)).containsExactly(folder.path("items").get(0).path("name").asString(), "alpha label");
		assertThat(folder.path("items").get(0).path("name").asString()).startsWith("Zulu");
		assertThat(beta).isNotNull();
	}

	@Test
	void anEntryWithoutCatalogIsLeftOut() {
		String folder = newFolder("Orphans", this.root);
		this.jdbc.update("""
				INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, created_by)
				VALUES (?::uuid, 'catalog', ?, ?)""", folder, UUID.randomUUID(), this.kurtId);
		assertThat(folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), folder).path("items").size()).isZero();
	}

	// Create a folder

	@Test
	void newFolder() {
		String name = "IT Landscape " + suffix();
		MvcTestResult result = createFolder(name, this.root);
		JsonNode folder = folder(created(result), id(result, name));
		assertThat(folder.path("name").asString()).isEqualTo(name);
		assertThat(folder.path("parentId").asString()).isEqualTo(this.root);
	}

	@Test
	void nameIsTrimmed() {
		String name = "Trimmed " + suffix();
		MvcTestResult result = createFolder("  " + name + "  ", this.root);
		assertThat(folder(created(result), id(result, name)).path("name").asString()).isEqualTo(name);
	}

	@Test
	void duplicateName() {
		String name = "IT Landscape " + suffix();
		created(createFolder(name, this.root));
		assertInvalid(createFolder(name.toLowerCase(java.util.Locale.ROOT), this.root), "name");
	}

	@Test
	void invalidNamesAndParents() {
		assertInvalid(createFolder("   ", this.root), "name");
		assertInvalid(createFolder("x".repeat(256), this.root), "name");
		created(createFolder(suffix() + "x".repeat(255 - suffix().length()), this.root));
		created(createFolder("Y", this.root));
		assertInvalid(createFolder("Tab\tName", this.root), "name");
		assertInvalid(createFolder("Orphan", UUID.randomUUID().toString()), "parentId");
	}

	// Rename a folder

	@Test
	void rename() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		created(place(landscape, inventory, null));
		String name = "Application Landscape " + suffix();
		JsonNode folder = folder(ok(rename(landscape, name)), landscape);
		assertThat(folder.path("name").asString()).isEqualTo(name);
		assertThat(folder.path("items").get(0).path("referenceId").asString()).isEqualTo(inventory);
	}

	@Test
	void renameToATakenNameAndUnknownFolder() {
		String taken = "Taken " + suffix();
		created(createFolder(taken, this.root));
		String other = newFolder("Other", this.root);
		assertInvalid(rename(other, taken.toUpperCase(java.util.Locale.ROOT)), "name");
		assertThat(rename(UUID.randomUUID().toString(), "Anything")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void theRootCanBeRenamed() {
		assertThat(ok(rename(this.root, "Architecture Repository")).path("name").asString())
			.isEqualTo("Architecture Repository");
		ok(rename(this.root, "Repository"));
	}

	// Delete a folder

	@Test
	void deleteWithSubfolders() {
		String landscape = newFolder("IT Landscape", this.root);
		String applications = newFolder("Applications", landscape);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		created(place(applications, inventory, null));
		JsonNode tree = ok(call(HttpMethod.DELETE, NODES + "/" + landscape, null, this.kurt));
		assertThat(find(tree, landscape)).isNull();
		assertThat(find(tree, applications)).isNull();
		assertThat(call(HttpMethod.GET, "/api/v1/catalogs/" + inventory, null, this.franz)).hasStatus(HttpStatus.OK);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'folders' || '/' || (details ->> 'items') FROM audit.events
				WHERE event_type = 'tree-node.deleted' AND target_id = ?""", String.class, landscape)).isEqualTo("2/1");
	}

	@Test
	void rootProtected() {
		MvcTestResult result = call(HttpMethod.DELETE, NODES + "/" + this.root, null, this.kurt);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("detail").asString()).isEqualTo("The root folder cannot be deleted.");
		assertThat(call(HttpMethod.DELETE, NODES + "/" + UUID.randomUUID(), null, this.kurt))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Place a catalog

	@Test
	void placeUnderAFolder() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		JsonNode tree = created(place(landscape, inventory, null));
		assertThat(folder(tree, landscape).path("items").get(0).path("referenceId").asString()).isEqualTo(inventory);
	}

	@Test
	void twoFoldersOneCatalog() {
		String landscape = newFolder("IT Landscape", this.root);
		String compliance = newFolder("Compliance", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		created(place(landscape, inventory, null));
		JsonNode tree = created(place(compliance, inventory, null));
		assertThat(folder(tree, landscape).path("items").get(0).path("referenceId").asString()).isEqualTo(inventory);
		assertThat(folder(tree, compliance).path("items").get(0).path("referenceId").asString()).isEqualTo(inventory);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM catalogs WHERE id = ?::uuid", Integer.class,
				inventory)).isEqualTo(1);
	}

	@Test
	void displayLabel() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		JsonNode item = folder(created(place(landscape, inventory, "  System Inventory ")), landscape).path("items")
			.get(0);
		assertThat(item.path("displayLabel").asString()).isEqualTo("System Inventory");
		assertThat(item.path("name").asString()).startsWith("Application Inventory");
		JsonNode catalog = ok(call(HttpMethod.GET, "/api/v1/catalogs/" + inventory, null, this.kurt));
		assertThat(catalog.path("name").asString()).startsWith("Application Inventory");
		assertInvalid(place(landscape, catalog("Long", "instance", this.kurt), "x".repeat(256)), "displayLabel");
		created(place(landscape, catalog("Longest", "instance", this.kurt), "x".repeat(255)));
	}

	@Test
	void aSecondEntryInTheSameFolder() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		created(place(landscape, inventory, null));
		assertInvalid(place(landscape, inventory, "Again"), "referenceId");
	}

	@Test
	void aCatalogThePersonCannotSee() {
		String landscape = newFolder("IT Landscape", this.root);
		String franzens = catalog("Franz's Candidates", "personal", this.franz);
		MvcTestResult hidden = place(landscape, franzens, null);
		MvcTestResult unknown = place(landscape, UUID.randomUUID().toString(), null);
		assertInvalid(hidden, "referenceId");
		assertInvalid(unknown, "referenceId");
		assertThat(body(hidden).path("detail").asString()).isEqualTo(body(unknown).path("detail").asString());
		assertThat(place(UUID.randomUUID().toString(), catalog("Somewhere", "instance", this.kurt), null))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Remove a catalog from the tree

	@Test
	void removeOnePlacement() {
		String landscape = newFolder("IT Landscape", this.root);
		String compliance = newFolder("Compliance", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		String item = folder(created(place(landscape, inventory, null)), landscape).path("items").get(0).path("id")
			.asString();
		created(place(compliance, inventory, null));
		JsonNode tree = ok(call(HttpMethod.DELETE, NODES + "/" + landscape + "/items/" + item, null, this.kurt));
		assertThat(folder(tree, landscape).path("items").size()).isZero();
		assertThat(folder(tree, compliance).path("items").get(0).path("referenceId").asString()).isEqualTo(inventory);
		assertThat(call(HttpMethod.GET, "/api/v1/catalogs/" + inventory, null, this.franz)).hasStatus(HttpStatus.OK);
		// the entry belongs to its folder only
		assertThat(call(HttpMethod.DELETE, NODES + "/" + compliance + "/items/" + item, null, this.kurt))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Who changes the tree

	@Test
	void readerWithoutTheRight() {
		String landscape = newFolder("IT Landscape", this.root);
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		String item = folder(created(place(landscape, inventory, null)), landscape).path("items").get(0).path("id")
			.asString();
		assertThat(call(HttpMethod.POST, NODES, folderBody("Franz's Folder", this.root), this.franz))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.PATCH, NODES + "/" + landscape,
				JsonMapper.shared().createObjectNode().put("name", "Renamed"), this.franz))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.DELETE, NODES + "/" + landscape, null, this.franz)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.POST, NODES + "/" + landscape + "/items", itemBody(inventory, null), this.franz))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.DELETE, NODES + "/" + landscape + "/items/" + item, null, this.franz))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM tree_nodes WHERE name = 'Franz''s Folder'",
				Integer.class)).isZero();
	}

	// Limits

	@Test
	void depth() {
		String parent = this.root;
		for (int level = 1; level <= NavigationTree.MAX_DEPTH; level++) {
			parent = newFolder("Level " + level, parent);
		}
		assertInvalid(createFolder("Level 11", parent), "parentId");
	}

	@Test
	void entriesPerFolder() {
		String folder = newFolder("Full", this.root);
		for (int i = 0; i < NavigationTree.MAX_ITEMS_PER_FOLDER - 1; i++) {
			this.jdbc.update("""
					INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, created_by)
					VALUES (?::uuid, 'catalog', ?, ?)""", folder, UUID.randomUUID(), this.kurtId);
		}
		created(place(folder, catalog("The Last One", "instance", this.kurt), null));
		assertInvalid(place(folder, catalog("One Too Many", "instance", this.kurt), null), "referenceId");
	}

	@Test
	void foldersOfTheTree() {
		String bulk = newFolder("Bulk", this.root);
		int existing = this.jdbc.queryForObject("SELECT count(*) FROM tree_nodes", Integer.class);
		try {
			for (int i = existing; i < NavigationTree.MAX_FOLDERS - 1; i++) {
				this.jdbc.update("INSERT INTO tree_nodes (name, parent_id, created_by) VALUES (?, ?::uuid, ?)",
						"Bulk " + i, bulk, this.kurtId);
			}
			created(createFolder("Folder 500", this.root));
			assertInvalid(createFolder("Folder 501", this.root), "parentId");
		}
		finally {
			ok(call(HttpMethod.DELETE, NODES + "/" + bulk, null, this.kurt));
		}
	}

	// Audit

	@Test
	void everyChangeIsAudited() {
		String folder = newFolder("Audited", this.root);
		ok(rename(folder, "Audited " + suffix()));
		String inventory = catalog("Application Inventory", "instance", this.kurt);
		String item = folder(created(place(folder, inventory, null)), folder).path("items").get(0).path("id")
			.asString();
		ok(call(HttpMethod.DELETE, NODES + "/" + folder + "/items/" + item, null, this.kurt));
		ok(call(HttpMethod.DELETE, NODES + "/" + folder, null, this.kurt));
		assertThat(this.jdbc.queryForList("""
				SELECT event_type FROM audit.events WHERE target_type = 'tree-node' AND target_id = ?
				  AND actor_person_id = ? ORDER BY id""", String.class, folder, this.kurtId))
			.containsExactly("tree-node.created", "tree-node.renamed", "tree-node.item-added", "tree-node.item-removed",
					"tree-node.deleted");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'referenceId' FROM audit.events
				WHERE event_type = 'tree-node.item-added' AND target_id = ?""", String.class, folder))
			.isEqualTo(inventory);
	}

	// New folders and entries go to the end

	@Test
	void newFoldersAndEntriesGoToTheEndAndTheOthersKeepTheirOrder() {
		String parent = newFolder("Appended", this.root);
		String first = newFolder("Zzz", parent);
		String second = newFolder("Aaa", parent);
		String entity = entity("Appended Entity " + suffix(), this.application, false);
		String solution = solution("Appended Solution");
		JsonNode tree = created(place(parent, catalog("Mmm", "instance", this.kurt), null));
		tree = created(placeEntity(parent, entity, null));
		tree = created(placeSolution(parent, solution, null));
		JsonNode folder = folder(tree, parent);
		assertThat(texts(folder.path("children"), "id")).containsExactly(first, second);
		assertThat(texts(folder.path("items"), "itemType")).containsExactly("catalog", "entity", "solution");
		assertThat(texts(folder.path("items"), "sortOrder")).containsExactly("0", "1", "2");
		String third = newFolder("Mmm", parent);
		assertThat(texts(folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), parent).path("children"), "id"))
			.containsExactly(first, second, third);
	}

	// Place an entity

	@Test
	void anEntityIsPlacedAtTheEndAndStaysUnchanged() {
		String finance = newFolder("Finance", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		created(place(finance, catalog("Before", "instance", this.kurt), null));
		JsonNode item = folder(created(placeEntity(finance, sap, " SAP label ")), finance).path("items").get(1);
		assertThat(item.path("itemType").asString()).isEqualTo("entity");
		assertThat(item.path("entityId").asInt()).isEqualTo(Integer.parseInt(sap));
		assertThat(item.path("referenceId").isNull()).isTrue();
		assertThat(item.path("displayLabel").asString()).isEqualTo("SAP label");
		assertThat(item.path("deleted").asBoolean()).isFalse();
		assertThat(item.path("scope").isNull()).isTrue();
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class,
				Integer.parseInt(sap))).isEqualTo(1);
	}

	@Test
	void theSameEntityInTwoFoldersButOnlyOnceInOne() {
		String finance = newFolder("Finance", this.root);
		String cloud = newFolder("Cloud Migration", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		created(placeEntity(finance, sap, null));
		JsonNode tree = created(placeEntity(cloud, sap, null));
		assertThat(folder(tree, finance).path("items").size()).isEqualTo(1);
		assertThat(folder(tree, cloud).path("items").size()).isEqualTo(1);
		assertInvalid(placeEntity(finance, sap, null), "entityId");
		assertThat(folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance).path("items").size())
			.isEqualTo(1);
	}

	@Test
	void aConnectionAnUnknownAndADeletedEntityAreRejected() {
		String finance = newFolder("Finance", this.root);
		String connection = entity("Orders " + suffix(), connectionType(), true);
		String deleted = entity("Gone " + suffix(), this.application, false);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId,
				Integer.parseInt(deleted));
		assertInvalid(placeEntity(finance, connection, null), "entityId");
		assertInvalid(placeEntity(finance, "2000000000", null), "entityId");
		assertInvalid(placeEntity(finance, deleted, null), "entityId");
		assertThat(folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance).path("items").size()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "entity,referenceId,entityId", "catalog,entityId,referenceId", "solution,entityId,referenceId" })
	void theFieldMustFitTheItemType(String itemType, String wrongField, String fittingField) {
		String finance = newFolder("Finance", this.root);
		ObjectNode body = JsonMapper.shared().createObjectNode().put("itemType", itemType);
		// the fitting field is missing, the other one is set
		if (wrongField.equals("entityId")) {
			body.put("entityId", 1);
		}
		else {
			body.put("referenceId", UUID.randomUUID().toString());
		}
		assertInvalid(call(HttpMethod.POST, NODES + "/" + finance + "/items", body, this.kurt), fittingField);
		// the fitting field is set together with the field of the other kind
		ObjectNode both = JsonMapper.shared().createObjectNode().put("itemType", itemType).put("entityId", 1)
			.put("referenceId", UUID.randomUUID().toString());
		assertInvalid(call(HttpMethod.POST, NODES + "/" + finance + "/items", both, this.kurt), wrongField);
	}

	// Place a solution

	@Test
	void aSolutionIsPlacedOncePerFolder() {
		String initiatives = newFolder("Initiatives", this.root);
		String crm = solution("CRM Replacement");
		JsonNode item = folder(created(placeSolution(initiatives, crm, null)), initiatives).path("items").get(0);
		assertThat(item.path("itemType").asString()).isEqualTo("solution");
		assertThat(item.path("referenceId").asString()).isEqualTo(crm);
		assertThat(item.path("name").asString()).startsWith("CRM Replacement");
		assertInvalid(placeSolution(initiatives, crm, null), "referenceId");
		assertInvalid(placeSolution(initiatives, UUID.randomUUID().toString(), null), "referenceId");
		String other = newFolder("Other", this.root);
		created(placeSolution(other, crm, null));
	}

	// Deleted entity in the tree

	@Test
	void aDeletedEntityStaysMarkedInEveryFolderUntilItIsRestored() {
		String finance = newFolder("Finance", this.root);
		String cloud = newFolder("Cloud Migration", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		created(placeEntity(finance, sap, null));
		created(placeEntity(cloud, sap, null));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId,
				Integer.parseInt(sap));
		JsonNode tree = ok(call(HttpMethod.GET, TREE, null, this.franz));
		assertThat(folder(tree, finance).path("items").get(0).path("deleted").asBoolean()).isTrue();
		assertThat(folder(tree, cloud).path("items").get(0).path("deleted").asBoolean()).isTrue();
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id = ?",
				Integer.parseInt(sap));
		tree = ok(call(HttpMethod.GET, TREE, null, this.franz));
		assertThat(folder(tree, finance).path("items").get(0).path("deleted").asBoolean()).isFalse();
	}

	@Test
	void removingOneEntityEntryKeepsTheOtherAndTheEntity() {
		String finance = newFolder("Finance", this.root);
		String cloud = newFolder("Cloud Migration", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String item = itemId(created(placeEntity(finance, sap, null)), finance, 0);
		created(placeEntity(cloud, sap, null));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId,
				Integer.parseInt(sap));
		JsonNode tree = ok(call(HttpMethod.DELETE, NODES + "/" + finance + "/items/" + item, null, this.kurt));
		assertThat(folder(tree, finance).path("items").size()).isZero();
		assertThat(folder(tree, cloud).path("items").size()).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id = ?", Integer.class,
				Integer.parseInt(sap))).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'entityId' || '/' || (details ->> 'itemType') || '/' || jsonb_exists(details, 'referenceId')
				FROM audit.events WHERE event_type = 'tree-node.item-removed' AND target_id = ?""", String.class,
				finance)).isEqualTo(sap + "/entity/false");
	}

	// Move a folder

	@Test
	void aFolderMovesWithItsContent() {
		String initiatives = newFolder("Initiatives", this.root);
		String cloud = newFolder("Cloud Migration", this.root);
		String sub = newFolder("Sub", cloud);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		created(placeEntity(cloud, sap, null));
		JsonNode tree = ok(moveFolder(cloud, initiatives, 0));
		JsonNode moved = folder(tree, cloud);
		assertThat(moved.path("parentId").asString()).isEqualTo(initiatives);
		assertThat(moved.path("items").size()).isEqualTo(1);
		assertThat(texts(moved.path("children"), "id")).containsExactly(sub);
		assertThat(texts(folder(tree, initiatives).path("children"), "id")).containsExactly(cloud);
		assertThat(find(ok(call(HttpMethod.GET, TREE, null, this.franz)), cloud).path("parentId").asString())
			.isEqualTo(initiatives);
	}

	@Test
	void aFolderMovesToAPositionAmongItsNewSiblings() {
		String parent = newFolder("Target", this.root);
		String one = newFolder("One", parent);
		String two = newFolder("Two", parent);
		String moving = newFolder("Moving", this.root);
		assertThat(texts(folder(ok(moveFolder(moving, parent, 1)), parent).path("children"), "id"))
			.containsExactly(one, moving, two);
		assertThat(texts(folder(ok(moveFolder(moving, parent, 0)), parent).path("children"), "id"))
			.containsExactly(moving, one, two);
		assertThat(texts(folder(ok(moveFolder(moving, parent, 10000)), parent).path("children"), "id"))
			.containsExactly(one, two, moving);
		assertThat(sortOrders("tree_nodes", "parent_id = ?::uuid", parent)).containsExactly(0, 1, 2);
		assertThat(sortOrders("tree_nodes", "parent_id = ?::uuid", this.root)).doesNotContain(-1);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void aFolderCannotMoveUnderItselfOrADescendant(int levelsBelow) {
		String top = newFolder("Initiatives", this.root);
		String target = top;
		for (int level = 0; level < levelsBelow; level++) {
			target = newFolder("Level " + level, target);
		}
		int before = auditCount();
		assertInvalid(moveFolder(top, target, 0), "parentId");
		assertThat(find(ok(call(HttpMethod.GET, TREE, null, this.kurt)), top).path("parentId").asString())
			.isEqualTo(this.root);
		assertThat(auditCount()).isEqualTo(before);
	}

	@Test
	void anUnknownParentATakenNameAndTheRootAreRejected() {
		String cloud = newFolder("Cloud", this.root);
		assertInvalid(moveFolder(cloud, UUID.randomUUID().toString(), 0), "parentId");
		String target = newFolder("Target", this.root);
		String taken = "Same " + suffix();
		created(createFolder(taken, target));
		created(createFolder(taken, this.root));
		String sameName = this.jdbc.queryForObject("SELECT id::text FROM tree_nodes WHERE name = ? AND parent_id = ?::uuid",
				String.class, taken, this.root);
		int before = auditCount();
		assertInvalid(moveFolder(sameName, target, 0), "parentId");
		assertThat(find(ok(call(HttpMethod.GET, TREE, null, this.kurt)), sameName).path("parentId").asString())
			.isEqualTo(this.root);
		assertThat(auditCount()).isEqualTo(before);
		MvcTestResult root = moveFolder(this.root, target, 0);
		assertThat(root).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(root).path("invalidFields").size()).isZero();
		assertThat(moveFolder(UUID.randomUUID().toString(), target, 0)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aFolderMoveToTheSamePlaceChangesAndAuditsNothing() {
		String parent = newFolder("Same Place", this.root);
		String one = newFolder("One", parent);
		newFolder("Two", parent);
		int before = auditCount();
		ok(moveFolder(one, parent, 0));
		assertThat(auditCount()).isEqualTo(before);
		ok(moveFolder(one, parent, 1));
		assertThat(auditCount()).isEqualTo(before + 1);
	}

	@Test
	void theDepthOfAMovedSubtreeIsLimited() {
		String moving = newFolder("Moving", this.root);
		newFolder("Child", moving);
		String parent = this.root;
		List<String> chain = new ArrayList<>();
		for (int level = 1; level <= 9; level++) {
			parent = newFolder("Level " + level, parent);
			chain.add(parent);
		}
		// Moving (1 level) with its child: under level 7 the child is 8 + 1 + 1 = at most 10 below the root
		ok(moveFolder(moving, chain.get(7), 0));
		assertThat(depthOf(moving)).isEqualTo(9);
		ok(moveFolder(moving, chain.get(6), 0));
		// under a folder 9 levels below the root the child would be 11 levels below it
		assertInvalid(moveFolder(moving, chain.get(8), 0), "parentId");
		// exactly 10 levels for the deepest folder
		ok(moveFolder(moving, chain.get(7), 0));
		assertThat(this.jdbc.queryForObject("SELECT parent_id::text FROM tree_nodes WHERE id = ?::uuid", String.class,
				moving)).isEqualTo(chain.get(7));
	}

	@Test
	void twoOppositeMovesAtTheSameTimeLeaveNoCycle() throws Exception {
		String initiatives = newFolder("Initiatives", this.root);
		String cloud = newFolder("Cloud Migration", this.root);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return moveFolder(initiatives, cloud, 0);
			});
			var second = pool.submit(() -> {
				start.await();
				return moveFolder(cloud, initiatives, 0);
			});
			start.countDown();
			List<HttpStatus> statuses = List.of(HttpStatus.valueOf(first.get().getResponse().getStatus()),
					HttpStatus.valueOf(second.get().getResponse().getStatus()));
			assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNPROCESSABLE_CONTENT);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(depthOf(initiatives)).isBetween(1, 2);
		assertThat(depthOf(cloud)).isBetween(1, 2);
		JsonNode tree = ok(call(HttpMethod.GET, TREE, null, this.kurt));
		assertThat(find(tree, initiatives)).isNotNull();
		assertThat(find(tree, cloud)).isNotNull();
	}

	// Move an entry

	@Test
	void anEntryIsReorderedWithinItsFolder() {
		String finance = newFolder("Finance", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String workday = entity("Workday " + suffix(), this.application, false);
		String salesforce = entity("Salesforce " + suffix(), this.application, false);
		created(placeEntity(finance, sap, null));
		created(placeEntity(finance, workday, null));
		JsonNode tree = created(placeEntity(finance, salesforce, null));
		String last = itemId(tree, finance, 2);
		assertThat(entityIds(folder(ok(moveItem(finance, last, finance, 0)), finance)))
			.containsExactly(salesforce, sap, workday);
		assertThat(entityIds(folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), finance)))
			.containsExactly(salesforce, sap, workday);
		// the position of the last entry gives the last place
		assertThat(entityIds(folder(ok(moveItem(finance, last, finance, 2)), finance)))
			.containsExactly(sap, workday, salesforce);
		// a position far past the end is clamped
		String first = itemId(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance, 0);
		assertThat(entityIds(folder(ok(moveItem(finance, first, finance, 10000)), finance)))
			.containsExactly(workday, salesforce, sap);
		assertThat(sortOrders("tree_node_items", "tree_node_id = ?::uuid", finance)).containsExactly(0, 1, 2);
	}

	@Test
	void anEntryMovesToAnotherFolder() {
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String workday = entity("Workday " + suffix(), this.application, false);
		String peoplesoft = entity("PeopleSoft " + suffix(), this.application, false);
		created(placeEntity(finance, sap, null));
		String item = itemId(created(placeEntity(finance, workday, null)), finance, 1);
		created(placeEntity(hr, peoplesoft, null));
		JsonNode tree = ok(moveItem(finance, item, hr, 0));
		assertThat(entityIds(folder(tree, hr))).containsExactly(workday, peoplesoft);
		assertThat(entityIds(folder(tree, finance))).containsExactly(sap);
		assertThat(sortOrders("tree_node_items", "tree_node_id = ?::uuid", hr)).containsExactly(0, 1);
		assertThat(sortOrders("tree_node_items", "tree_node_id = ?::uuid", finance)).containsExactly(0);
		// an entry is addressed by its own folder
		assertThat(moveItem(finance, item, hr, 0)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void anUnknownTargetFolderAndATargetAlreadyThereAreRejected() {
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String item = itemId(created(placeEntity(finance, sap, null)), finance, 0);
		created(placeEntity(hr, sap, null));
		int before = auditCount();
		assertInvalid(moveItem(finance, item, UUID.randomUUID().toString(), 0), "nodeId");
		assertInvalid(moveItem(finance, item, hr, 0), "nodeId");
		assertThat(auditCount()).isEqualTo(before);
		assertThat(entityIds(folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance)))
			.containsExactly(sap);
	}

	@Test
	void aMoveToTheSamePlaceIsAnswered200WithoutAnAuditEvent() {
		String finance = newFolder("Finance", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String workday = entity("Workday " + suffix(), this.application, false);
		String item = itemId(created(placeEntity(finance, sap, null)), finance, 0);
		created(placeEntity(finance, workday, null));
		int before = auditCount();
		JsonNode tree = ok(moveItem(finance, item, finance, 0));
		assertThat(entityIds(folder(tree, finance))).containsExactly(sap, workday);
		assertThat(auditCount()).isEqualTo(before);
	}

	@Test
	void thePositionCountsOnlyTheEntriesTheCallerSees() {
		String finance = newFolder("Finance", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String workday = entity("Workday " + suffix(), this.application, false);
		TestSession maria = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		created(placeEntity(finance, sap, null));
		created(place(finance, catalog("Kurt's Candidates", "personal", this.kurt), null));
		String item = itemId(created(placeEntity(finance, workday, null)), finance, 2);
		MvcTestResult result = call(HttpMethod.PUT, NODES + "/" + finance + "/items/" + item + "/position",
				JsonMapper.shared().createObjectNode().put("nodeId", finance).put("position", 0), maria);
		JsonNode mariasTree = ok(result);
		assertThat(folder(mariasTree, finance).path("items").size()).isEqualTo(2);
		assertThat(entityIds(folder(mariasTree, finance))).containsExactly(workday, sap);
		JsonNode kurts = folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance);
		assertThat(texts(kurts.path("items"), "itemType")).containsExactly("entity", "entity", "catalog");
		assertThat(kurts.path("items").get(0).path("entityId").asInt()).isEqualTo(Integer.parseInt(workday));
		assertThat(kurts.path("items").get(1).path("entityId").asInt()).isEqualTo(Integer.parseInt(sap));
	}

	@Test
	void anotherPersonsPersonalCatalogEntryCannotBeMovedOrRemoved() {
		String finance = newFolder("Finance", this.root);
		String candidates = catalog("Kurt's Candidates", "personal", this.kurt);
		String item = itemId(created(place(finance, candidates, null)), finance, 0);
		TestSession maria = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		assertThat(call(HttpMethod.DELETE, NODES + "/" + finance + "/items/" + item, null, maria))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(call(HttpMethod.PUT, NODES + "/" + finance + "/items/" + item + "/position",
				JsonMapper.shared().createObjectNode().put("nodeId", finance).put("position", 0), maria))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(folder(ok(call(HttpMethod.GET, TREE, null, this.kurt)), finance).path("items").size())
			.isEqualTo(1);
	}

	@Test
	void unknownFolderAndEntryInThePathAnswer404() {
		String finance = newFolder("Finance", this.root);
		assertThat(moveItem(UUID.randomUUID().toString(), UUID.randomUUID().toString(), finance, 0))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(moveItem(finance, UUID.randomUUID().toString(), finance, 0)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(call(HttpMethod.DELETE, NODES + "/" + finance + "/items/" + UUID.randomUUID(), null, this.kurt))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Limits

	@Test
	void entriesPerFolderOnMoves() {
		String full = newFolder("Full", this.root);
		String almost = newFolder("Almost", this.root);
		String source = newFolder("Source", this.root);
		for (int i = 0; i < NavigationTree.MAX_ITEMS_PER_FOLDER - 1; i++) {
			junkItem(almost);
		}
		for (int i = 0; i < NavigationTree.MAX_ITEMS_PER_FOLDER; i++) {
			junkItem(full);
		}
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String workday = entity("Workday " + suffix(), this.application, false);
		created(placeEntity(source, sap, null));
		JsonNode tree = created(placeEntity(source, workday, null));
		String first = itemId(tree, source, 0);
		String second = itemId(tree, source, 1);
		ok(moveItem(source, first, almost, 0));
		assertInvalid(moveItem(source, second, almost, 0), "nodeId");
		assertInvalid(moveItem(source, second, full, 0), "nodeId");
		assertInvalid(placeEntity(full, sap, null), "entityId");
		assertInvalid(placeSolution(full, solution("Full Solution"), null), "referenceId");
		// reordering in a folder that holds 200 entries
		String twoReal = newFolder("Full With Two", this.root);
		for (int i = 0; i < NavigationTree.MAX_ITEMS_PER_FOLDER - 2; i++) {
			junkItem(twoReal);
		}
		created(placeEntity(twoReal, sap, null));
		JsonNode filled = created(placeEntity(twoReal, workday, null));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM tree_node_items WHERE tree_node_id = ?::uuid",
				Integer.class, twoReal)).isEqualTo(NavigationTree.MAX_ITEMS_PER_FOLDER);
		ok(moveItem(twoReal, itemId(filled, twoReal, 1), twoReal, 0));
	}

	@ParameterizedTest
	@CsvSource({ "0,200", "10000,200", "-1,422", "10001,422" })
	void positionBoundaries(int position, int expected) {
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String item = itemId(created(placeEntity(finance, sap, null)), finance, 0);
		MvcTestResult item_ = moveItem(finance, item, hr, position);
		MvcTestResult node = moveFolder(hr, finance, position);
		if (expected == 200) {
			assertThat(item_).as(() -> content(item_)).hasStatus(HttpStatus.OK);
			assertThat(node).as(() -> content(node)).hasStatus(HttpStatus.OK);
		}
		else {
			assertInvalid(item_, "position");
			assertInvalid(node, "position");
		}
	}

	@Test
	void aMissingPositionIsRejected() {
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String item = itemId(created(placeEntity(finance, sap, null)), finance, 0);
		assertInvalid(call(HttpMethod.PUT, NODES + "/" + finance + "/items/" + item + "/position",
				JsonMapper.shared().createObjectNode().put("nodeId", hr), this.kurt), "position");
		assertInvalid(call(HttpMethod.PUT, NODES + "/" + hr + "/position",
				JsonMapper.shared().createObjectNode().put("parentId", finance), this.kurt), "position");
	}

	// Permissions

	@Test
	void everyTreeWriteNeedsEntityReadInAdditionToViewpointManage() {
		String role = "manage-only-" + suffix();
		this.jdbc.update("INSERT INTO authorization_roles (id, name, is_builtin) VALUES (?, ?, false)", role, role);
		this.jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, 'viewpoint:manage')", role);
		TestSession eva = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole(role);
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String sap = entity("SAP ERP " + suffix(), this.application, false);
		String inventory = catalog("Inventory", "instance", this.kurt);
		String solution = solution("CRM");
		JsonNode tree = created(placeEntity(finance, sap, null));
		String item = itemId(tree, finance, 0);
		int folders = this.jdbc.queryForObject("SELECT count(*) FROM tree_nodes", Integer.class);
		int items = this.jdbc.queryForObject("SELECT count(*) FROM tree_node_items", Integer.class);
		int events = auditCount();
		String items_ = NODES + "/" + finance + "/items";
		List<MvcTestResult> results = List.of(
				call(HttpMethod.POST, NODES, folderBody("Eva", this.root), eva),
				call(HttpMethod.PATCH, NODES + "/" + hr, JsonMapper.shared().createObjectNode().put("name", "Eva"), eva),
				call(HttpMethod.DELETE, NODES + "/" + hr, null, eva),
				call(HttpMethod.POST, items_, itemBody(inventory, null), eva),
				call(HttpMethod.POST, items_, JsonMapper.shared().createObjectNode().put("itemType", "entity")
					.put("entityId", Integer.parseInt(sap)), eva),
				call(HttpMethod.POST, items_, JsonMapper.shared().createObjectNode().put("itemType", "solution")
					.put("referenceId", solution), eva),
				call(HttpMethod.DELETE, items_ + "/" + item, null, eva),
				call(HttpMethod.PUT, NODES + "/" + hr + "/position",
						JsonMapper.shared().createObjectNode().put("parentId", finance).put("position", 0), eva),
				call(HttpMethod.PUT, items_ + "/" + item + "/position",
						JsonMapper.shared().createObjectNode().put("nodeId", hr).put("position", 0), eva));
		results.forEach((result) -> assertThat(result).hasStatus(HttpStatus.FORBIDDEN));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM tree_nodes", Integer.class)).isEqualTo(folders);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM tree_node_items", Integer.class)).isEqualTo(items);
		assertThat(auditCount()).isEqualTo(events);
		// a reader cannot move either
		assertThat(call(HttpMethod.PUT, NODES + "/" + hr + "/position",
				JsonMapper.shared().createObjectNode().put("parentId", finance).put("position", 0), this.franz))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Audit

	@Test
	void movesAndPlacementsAreAuditedWithoutLabelsAndNames() {
		String finance = newFolder("Finance", this.root);
		String hr = newFolder("HR", this.root);
		String name = "Secret Name " + suffix();
		String sap = entity(name, this.application, false);
		String solution = solution("Secret Solution");
		JsonNode tree = created(placeEntity(finance, sap, "Secret Label"));
		created(placeSolution(finance, solution, "Solution Label"));
		String item = itemId(tree, finance, 0);
		ok(moveItem(finance, item, hr, 3));
		ok(moveFolder(hr, finance, 0));
		assertThat(this.jdbc.queryForList("""
				SELECT event_type FROM audit.events WHERE event_type IN ('tree-node.item-moved', 'tree-node.moved')
				  AND target_id IN (?, ?) ORDER BY id""", String.class, finance, hr))
			.containsExactly("tree-node.item-moved", "tree-node.moved");
		String moved = this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'tree-node.item-moved' AND target_id = ?""",
				String.class, finance);
		JsonNode details = JsonMapper.shared().readTree(moved);
		assertThat(details.path("fromNodeId").asString()).isEqualTo(finance);
		assertThat(details.path("toNodeId").asString()).isEqualTo(hr);
		assertThat(details.path("position").asInt()).isZero();
		assertThat(details.path("itemType").asString()).isEqualTo("entity");
		String folderMoved = this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'tree-node.moved' AND target_id = ?""",
				String.class, hr);
		assertThat(JsonMapper.shared().readTree(folderMoved).path("fromNodeId").asString()).isEqualTo(this.root);
		assertThat(JsonMapper.shared().readTree(folderMoved).path("toNodeId").asString()).isEqualTo(finance);
		List<String> added = this.jdbc.queryForList("""
				SELECT details::text FROM audit.events WHERE event_type = 'tree-node.item-added' AND target_id = ?
				ORDER BY id""", String.class, finance);
		assertThat(added).hasSize(2);
		assertThat(JsonMapper.shared().readTree(added.get(0)).path("itemType").asString()).isEqualTo("entity");
		assertThat(JsonMapper.shared().readTree(added.get(0)).path("entityId").asInt()).isEqualTo(Integer.parseInt(sap));
		assertThat(JsonMapper.shared().readTree(added.get(1)).path("referenceId").asString()).isEqualTo(solution);
		String all = this.jdbc.queryForList("SELECT details::text FROM audit.events WHERE target_type = 'tree-node'",
				String.class).toString();
		assertThat(all).doesNotContain("Secret Label", "Solution Label", name, "Secret Solution");
	}

	// helpers

	private MvcTestResult placeEntity(String nodeId, String entityId, String label) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("itemType", "entity").put("entityId",
				Integer.parseInt(entityId));
		if (label != null) {
			body.put("displayLabel", label);
		}
		return call(HttpMethod.POST, NODES + "/" + nodeId + "/items", body, this.kurt);
	}

	private MvcTestResult placeSolution(String nodeId, String solutionId, String label) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("itemType", "solution").put("referenceId",
				solutionId);
		if (label != null) {
			body.put("displayLabel", label);
		}
		return call(HttpMethod.POST, NODES + "/" + nodeId + "/items", body, this.kurt);
	}

	private MvcTestResult moveFolder(String nodeId, String parentId, int position) {
		return call(HttpMethod.PUT, NODES + "/" + nodeId + "/position",
				JsonMapper.shared().createObjectNode().put("parentId", parentId).put("position", position), this.kurt);
	}

	private MvcTestResult moveItem(String nodeId, String itemId, String targetNodeId, int position) {
		return call(HttpMethod.PUT, NODES + "/" + nodeId + "/items/" + itemId + "/position",
				JsonMapper.shared().createObjectNode().put("nodeId", targetNodeId).put("position", position),
				this.kurt);
	}

	/** Creates an entity of the metatype and returns its ID. */
	private String entity(String name, UUID metatype, boolean unused) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id::text""", String.class,
				metatype, name, this.kurtId);
	}

	private UUID connectionType() {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, true) RETURNING id""", UUID.class,
				"Data Flow " + suffix());
	}

	private String solution(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.kurtId, this.kurtId);
	}

	/** An entry no caller sees (a catalog that does not exist); it counts towards the limit of a folder. */
	private void junkItem(String folder) {
		this.jdbc.update("""
				INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, sort_order, created_by)
				VALUES (?::uuid, 'catalog', ?, (SELECT count(*) FROM tree_node_items WHERE tree_node_id = ?::uuid), ?)""",
				folder, UUID.randomUUID(), folder, this.kurtId);
	}

	private static String createdId(MvcTestResult result, String name) {
		return id(result, name);
	}

	private static String itemId(JsonNode tree, String folderId, int index) {
		return folder(tree, folderId).path("items").get(index).path("id").asString();
	}

	private static List<String> entityIds(JsonNode folder) {
		List<String> ids = new ArrayList<>();
		folder.path("items").forEach((item) -> ids.add(item.path("entityId").asString()));
		return ids;
	}

	private static List<String> labels(JsonNode folder) {
		List<String> labels = new ArrayList<>();
		folder.path("items").forEach((item) -> labels.add(item.path("displayLabel").isNull()
				? item.path("name").asString() : item.path("displayLabel").asString()));
		return labels;
	}

	private List<Integer> sortOrders(String table, String where, String parameter) {
		return this.jdbc.queryForList("SELECT sort_order FROM " + table + " WHERE " + where + " ORDER BY sort_order",
				Integer.class, parameter);
	}

	private int auditCount() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type LIKE 'tree-node.%'",
				Integer.class);
	}

	private int depthOf(String folder) {
		return this.jdbc.queryForObject("""
				WITH RECURSIVE up (id, parent_id, depth) AS (
				    SELECT id, parent_id, 0 FROM tree_nodes WHERE id = ?::uuid
				    UNION ALL
				    SELECT n.id, n.parent_id, up.depth + 1 FROM tree_nodes n JOIN up ON n.id = up.parent_id
				    WHERE up.depth < 50)
				SELECT max(depth) FROM up""", Integer.class, folder);
	}

	private MvcTestResult createFolder(String name, String parentId) {
		return call(HttpMethod.POST, NODES, folderBody(name, parentId), this.kurt);
	}

	private MvcTestResult rename(String nodeId, String name) {
		return call(HttpMethod.PATCH, NODES + "/" + nodeId, JsonMapper.shared().createObjectNode().put("name", name),
				this.kurt);
	}

	private MvcTestResult place(String nodeId, String catalogId, String label) {
		return call(HttpMethod.POST, NODES + "/" + nodeId + "/items", itemBody(catalogId, label), this.kurt);
	}

	private static ObjectNode folderBody(String name, String parentId) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("parentId", parentId);
	}

	private static ObjectNode itemBody(String catalogId, String label) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("itemType", "catalog").put("referenceId", catalogId);
		if (label != null) {
			body.put("displayLabel", label);
		}
		return body;
	}

	/** Creates the folder "prefix suffix" under the parent and returns its ID. */
	private String newFolder(String prefix, String parentId) {
		String name = prefix + " " + suffix();
		return id(createFolder(name, parentId), name);
	}

	/** The ID of the folder with the name, from the tree in the response; names carry a random suffix. */
	private static String id(MvcTestResult result, String name) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		List<JsonNode> matches = new ArrayList<>();
		collect(body(result), name.strip(), matches);
		assertThat(matches).as(name).hasSize(1);
		return matches.get(0).path("id").asString();
	}

	private static void collect(JsonNode node, String name, List<JsonNode> matches) {
		if (node.path("name").asString().equals(name)) {
			matches.add(node);
		}
		node.path("children").forEach((child) -> collect(child, name, matches));
	}

	private static JsonNode folder(JsonNode tree, String id) {
		JsonNode found = find(tree, id);
		assertThat(found).as("folder " + id).isNotNull();
		return found;
	}

	private static JsonNode find(JsonNode node, String id) {
		if (node.path("id").asString().equals(id)) {
			return node;
		}
		for (JsonNode child : node.path("children")) {
			JsonNode found = find(child, id);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static List<String> texts(JsonNode array, String field) {
		List<String> texts = new ArrayList<>();
		array.forEach((node) -> texts.add(node.path(field).asString()));
		return texts;
	}

	private String catalog(String name, String scope, TestSession session) {
		MvcTestResult created = call(HttpMethod.POST, "/api/v1/catalogs", JsonMapper.shared()
			.createObjectNode()
			.put("name", name + " " + suffix())
			.put("primaryMetatypeId", this.application.toString())
			.put("scope", scope), session);
		assertThat(created).as(() -> content(created)).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode body(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-navigation").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
