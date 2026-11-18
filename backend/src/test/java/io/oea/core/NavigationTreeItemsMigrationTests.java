package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.config.migration.MigrationSet;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Migration db/migration/V40__navigation_tree_items.sql (us-095-navigation-tree-items D1): the
 * checks of item type and reference for both kinds, one entity once per folder, and the starting
 * order by name of existing folders and entries.
 */
class NavigationTreeItemsMigrationTests {

	private static final String INSTANCE_CONFIG = "00000000-0000-0000-0000-000000000001";

	private static OeaDatabase postgres;

	private static long personId;

	private static String rootId;

	private static int entityId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		personId = Long.parseLong(single(postgres, "INSERT INTO persons (name, email) VALUES ('Kurt', "
				+ "'kurt-items@example.org') RETURNING id"));
		rootId = single(postgres, "SELECT id::text FROM tree_nodes WHERE parent_id IS NULL");
		entityId = Integer.parseInt(entity(postgres, "SAP ERP"));
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void anEntityEntryNeedsTheEntityAndNoReference() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String folder = folder(statement, "Entity Checks", rootId);
			statement.executeUpdate(item(folder, "entity", null, entityId));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, "entity", null, null)))
				.withMessageContaining("tree_node_items_reference");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, "entity", UUID.randomUUID(), null)))
				.withMessageContaining("tree_node_items_reference");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, "entity", UUID.randomUUID(), entityId + 1)))
				.withMessageContaining("tree_node_items_reference");
		}
	}

	@ParameterizedTest
	@CsvSource({ "catalog", "solution", "diagram" })
	void anyOtherEntryNeedsTheReferenceAndNoEntity(String itemType) throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String folder = folder(statement, "Reference Checks " + itemType, rootId);
			statement.executeUpdate(item(folder, itemType, UUID.randomUUID(), null));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, itemType, null, null)))
				.withMessageContaining("tree_node_items_reference");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, itemType, UUID.randomUUID(), entityId)))
				.withMessageContaining("tree_node_items_reference");
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, itemType, null, entityId)))
				.withMessageContaining("tree_node_items_reference");
		}
	}

	@Test
	void theItemTypeIsLimited() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String folder = folder(statement, "Types", rootId);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, "plateau", UUID.randomUUID(), null)))
				.withMessageContaining("tree_node_items_item_type_check");
		}
	}

	@Test
	void anEntityIsPlacedOncePerFolderButInAnyNumberOfFolders() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String first = folder(statement, "Once First", rootId);
			String second = folder(statement, "Once Second", rootId);
			statement.executeUpdate(item(first, "entity", null, entityId));
			statement.executeUpdate(item(second, "entity", null, entityId));
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(first, "entity", null, entityId)))
				.withMessageContaining("tree_node_items_entity_once_per_folder");
		}
	}

	@Test
	void theEntityMustExist() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String folder = folder(statement, "Unknown Entity", rootId);
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(item(folder, "entity", null, 2_000_000_000)))
				.withMessageContaining("foreign key");
		}
	}

	@Test
	void theStartingOrderFollowsTheNamesAndExistingEntriesStay() throws Exception {
		OeaDatabase old = OeaDatabases.freshProvisioned();
		try {
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.target("39")
				.load()
				.migrate();
			long person = Long.parseLong(single(old, "INSERT INTO persons (name, email) VALUES ('Kurt', "
					+ "'kurt-old@example.org') RETURNING id"));
			String root = single(old, "SELECT id::text FROM tree_nodes WHERE parent_id IS NULL");
			String metatype = single(old, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
					+ "', 'Application') RETURNING id::text");
			String zebra = single(old, folderSql("zebra", root, person));
			String alpha = single(old, folderSql("Alpha", root, person));
			String beta = single(old, folderSql("beta", root, person));
			String child = single(old, folderSql("Child", zebra, person));
			String zulu = single(old, catalogSql("Zulu", metatype, person));
			String apple = single(old, catalogSql("apple", metatype, person));
			String mango = single(old, catalogSql("Mango", metatype, person));
			String zuluItem = single(old, oldItemSql(alpha, zulu, null, person));
			String appleItem = single(old, oldItemSql(alpha, apple, null, person));
			String mangoItem = single(old, oldItemSql(alpha, mango, "Beta label", person));
			String lonely = single(old, oldItemSql(child, zulu, null, person));
			MigrationSet.CORE.flyway()
				.dataSource(old.getJdbcUrl(), "oea_owner", OeaPostgres.ROLE_PASSWORDS.get("oea_owner"))
				.load()
				.migrate();
			assertThat(orders(old, "tree_nodes", "parent_id = '" + root + "'")).containsExactly(alpha + "=0",
					beta + "=1", zebra + "=2");
			assertThat(orders(old, "tree_nodes", "id = '" + root + "'")).containsExactly(root + "=0");
			assertThat(orders(old, "tree_node_items", "tree_node_id = '" + alpha + "'"))
				.containsExactly(appleItem + "=0", mangoItem + "=1", zuluItem + "=2");
			assertThat(orders(old, "tree_node_items", "tree_node_id = '" + child + "'"))
				.containsExactly(lonely + "=0");
			assertThat(single(old, "SELECT count(*) FROM tree_node_items WHERE item_type = 'catalog' "
					+ "AND reference_id IS NOT NULL AND entity_id IS NULL")).isEqualTo("4");
			assertThat(single(old, "SELECT display_label FROM tree_node_items WHERE id = '" + mangoItem + "'"))
				.isEqualTo("Beta label");
		}
		finally {
			old.stop();
		}
	}

	private static List<String> orders(OeaDatabase database, String table, String where) throws SQLException {
		List<String> orders = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement
					.executeQuery("SELECT id::text, sort_order FROM " + table + " WHERE " + where
							+ " ORDER BY sort_order, id")) {
			while (rs.next()) {
				orders.add(rs.getString(1) + "=" + rs.getInt(2));
			}
		}
		return orders;
	}

	private static String folderSql(String name, String parent, long person) {
		return "INSERT INTO tree_nodes (name, parent_id, created_by) VALUES ('" + name + "', '" + parent + "', "
				+ person + ") RETURNING id::text";
	}

	private static String catalogSql(String name, String metatype, long person) {
		return "INSERT INTO catalogs (name, primary_metatype_id, created_by, last_modified_by) VALUES ('" + name
				+ "', '" + metatype + "', " + person + ", " + person + ") RETURNING id::text";
	}

	private static String oldItemSql(String folder, String catalog, String label, long person) {
		return "INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, display_label, created_by) "
				+ "VALUES ('" + folder + "', 'catalog', '" + catalog + "', "
				+ ((label != null) ? "'" + label + "'" : "NULL") + ", " + person + ") RETURNING id::text";
	}

	private static String folder(Statement statement, String name, String parentId) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO tree_nodes (name, parent_id, created_by) VALUES ('"
				+ name + "', '" + parentId + "', " + personId + ") RETURNING id::text")) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static String item(String nodeId, String itemType, UUID referenceId, Integer entity) {
		return "INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, entity_id, created_by) VALUES ('"
				+ nodeId + "', '" + itemType + "', " + ((referenceId != null) ? "'" + referenceId + "'" : "NULL")
				+ ", " + ((entity != null) ? entity.toString() : "NULL") + ", " + personId + ")";
	}

	private static String entity(OeaDatabase database, String name) throws SQLException {
		String metatype = single(database, "INSERT INTO metatypes (config_id, name) VALUES ('" + INSTANCE_CONFIG
				+ "', 'Application " + UUID.randomUUID() + "') RETURNING id::text");
		return single(database, "INSERT INTO entities (metatype_id, name, created_by) VALUES ('" + metatype + "', '"
				+ name + "', " + personId + ") RETURNING id");
	}

	private static String single(OeaDatabase database, String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(database); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
