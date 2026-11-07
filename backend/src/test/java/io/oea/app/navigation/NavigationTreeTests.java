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
	void foldersAndEntriesAreSortedByNameRegardlessOfCase() {
		String parent = newFolder("Sorted", this.root);
		createFolder("beta", parent);
		createFolder("Alpha", parent);
		createFolder("Gamma", parent);
		created(place(parent, catalog("Zulu", "instance", this.kurt), null));
		created(place(parent, catalog("Mike", "instance", this.kurt), "alpha label"));
		JsonNode folder = folder(ok(call(HttpMethod.GET, TREE, null, this.franz)), parent);
		assertThat(texts(folder.path("children"), "name")).containsExactly("Alpha", "beta", "Gamma");
		List<String> labels = new ArrayList<>();
		folder.path("items").forEach((item) -> labels.add(item.path("displayLabel").isNull()
				? item.path("name").asString() : item.path("displayLabel").asString()));
		assertThat(labels.get(0)).isEqualTo("alpha label");
		assertThat(labels.get(1)).startsWith("Zulu");
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

	// helpers

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
