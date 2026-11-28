package io.oea.app.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Specs lineage/lineage-query (us-065-lineage-model-and-query D4): the walk from a data object and from a system, the
 * tasks, the limits, the filter, the errors and the access, at API level with the TOGAF and the BPMN package. Each
 * test builds a graph of its own, so the tests are independent; only the one that changes the visibility of
 * "Transformation type" is ordered last.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineageQueryTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String QUERY = "/api/v1/views/lineage/query";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private static final Map<String, String> types = new HashMap<>();

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

	private Api api;

	private TestPrincipals principals;

	private TestSession admin;

	private TestSession anna;

	private TestSession franz;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = this.principals.personWithIdpRole("metamodel-admin");
		this.anna = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
	}

	@Test
	@Order(1)
	void thePackagesAreImportedAndTheLineageModelIsFilled() {
		for (String id : List.of("oea-starter-togaf-classic", "oea-starter-bpmn")) {
			this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + id + "/import", null, this.admin));
		}
		JsonNode model = this.api.ok(this.api.get("/api/v1/metamodel/lineage-model", this.franz));
		for (String part : List.of("dataObject", "dataFlow", "carriesData", "dataInput", "dataOutput")) {
			assertThat(model.path(part).path("metatypeId").isNull()).as(part).isFalse();
		}
	}

	// ── from a data object ────────────────────────────────────────────────────

	/** direction, nodes as name:depth:roles, the path as names, the edges */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"downstream | SAP ERP:0:origin;DWH:1:intermediate;BI Tool:2:sink | SAP ERP>DWH>BI Tool | 2",
			"upstream | BI Tool:0:sink;DWH:1:intermediate;SAP ERP:2:origin | BI Tool>DWH>SAP ERP | 2",
			"both | SAP ERP:0:origin;DWH:1:intermediate;BI Tool:0:sink | SAP ERP>DWH>BI Tool | 2" })
	@Order(2)
	void aDataObjectIsFollowedDownstreamUpstreamAndInBothDirections(String direction, String nodes, String path,
			int edges) {
		Chain chain = chain();
		JsonNode result = ok(this.franz, body(chain.object).put("direction", direction));
		assertThat(result.path("start").path("id").asInt()).isEqualTo(chain.object);
		assertThat(result.path("direction").asString()).isEqualTo(direction);
		assertThat(describe(result)).containsExactlyInAnyOrder(nodes.split(";"));
		assertThat(paths(result)).containsExactly(path);
		assertThat(result.path("edges")).hasSize(edges);
		for (JsonNode edge : result.path("edges")) {
			assertThat(edge.path("kind").asString()).isEqualTo("data-flow");
			assertThat(edge.path("carriedBy")).hasSize(1);
			assertThat(edge.path("carriedBy").get(0).path("dataObjectName").asString()).isEqualTo("Customer Master");
			assertThat(edge.path("carriedBy").get(0).path("connectionId").asInt()).isPositive();
			assertThat(edge.path("carriedByTruncated").asBoolean()).isFalse();
		}
		assertThat(result.path("cycleDetected").asBoolean()).isFalse();
		assertThat(result.path("truncated").asBoolean()).isFalse();
		assertThat(result.path("nodes").valueStream().map((node) -> node.path("id").asInt())).doesNotContain(chain.object);
	}

	@Test
	@Order(3)
	void theDefaultDirectionIsBoth() {
		Chain chain = chain();
		JsonNode result = ok(this.franz, body(chain.object));
		assertThat(result.path("direction").asString()).isEqualTo("both");
		assertThat(names(result)).containsExactlyInAnyOrder("SAP ERP", "DWH", "BI Tool");
	}

	@Test
	@Order(4)
	void aDataObjectThatNoFlowCarriesGives200WithNothing() {
		int object = entity("Data Object", "Lonely");
		JsonNode result = ok(this.franz, body(object));
		assertThat(result.path("nodes")).isEmpty();
		assertThat(result.path("edges")).isEmpty();
		assertThat(result.path("paths")).isEmpty();
		assertThat(result.path("cycleDetected").asBoolean()).isFalse();
		assertThat(result.path("truncated").asBoolean()).isFalse();
	}

	/** what is deleted, the nodes that remain downstream */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "the flow DWH to BI | SAP ERP,DWH", "its carries-data connection | SAP ERP,DWH",
			"the endpoint BI Tool | SAP ERP,DWH", "the endpoint DWH | -", "the first flow | DWH,BI Tool" })
	@Order(5)
	void aDeletedFlowCarryingConnectionOrEndpointIsLeftOut(String what, String remaining) {
		Chain chain = chain();
		List<Integer> gone = switch (what) {
			case "the flow DWH to BI" -> List.of(chain.second);
			case "its carries-data connection" -> List.of(chain.second);
			case "the endpoint BI Tool" -> List.of(chain.second);
			case "the endpoint DWH" -> List.of(chain.first, chain.second);
			default -> List.of(chain.first);
		};
		switch (what) {
			case "the flow DWH to BI" -> delete(chain.second);
			case "its carries-data connection" -> delete(chain.secondCarries);
			case "the endpoint BI Tool" -> delete(chain.bi);
			case "the endpoint DWH" -> delete(chain.dwh);
			default -> delete(chain.first);
		}
		JsonNode result = ok(this.franz, body(chain.object).put("direction", "downstream"));
		List<String> expected = "-".equals(remaining) ? List.of() : List.of(remaining.split(","));
		assertThat(names(result)).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(result.path("edges").valueStream().map((edge) -> edge.path("id").asInt()))
			.doesNotContainAnyElementsOf(gone);
	}

	@Test
	@Order(6)
	void aCycleEndsAtTheLowestIdAsOriginAndIsDetected() {
		int object = entity("Data Object", "Cyclic");
		int a = entity("Application Component", "A");
		int b = entity("Application Component", "B");
		int c = entity("Application Component", "C");
		carryingFlow(a, b, object);
		carryingFlow(b, c, object);
		carryingFlow(c, a, object);
		JsonNode result = ok(this.franz, body(object).put("direction", "downstream"));
		assertThat(result.path("cycleDetected").asBoolean()).isTrue();
		assertThat(describeDepths(result)).containsExactlyInAnyOrder("A:0", "B:1", "C:2");
		assertThat(result.path("edges")).hasSize(3);
	}

	@Test
	@Order(7)
	void aDiamondIsNoCycleAndHasTwoPaths() {
		int object = entity("Data Object", "Diamond");
		int a = entity("Application Component", "A");
		int b = entity("Application Component", "B");
		int c = entity("Application Component", "C");
		int d = entity("Application Component", "D");
		carryingFlow(a, b, object);
		carryingFlow(a, c, object);
		carryingFlow(b, d, object);
		carryingFlow(c, d, object);
		JsonNode result = ok(this.franz, body(object).put("direction", "downstream"));
		assertThat(result.path("cycleDetected").asBoolean()).isFalse();
		assertThat(describeDepths(result)).containsExactlyInAnyOrder("A:0", "B:1", "C:1", "D:2");
		assertThat(paths(result)).containsExactlyInAnyOrder("A>B>D", "A>C>D");
		assertThat(result.path("edges")).hasSize(4);
	}

	// ── the tasks of the data associations ───────────────────────────────────

	/** direction, the roles of the task, the kinds of the edges */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "both | reader,writer | data-input,data-output",
			"downstream | reader | data-input", "impact | reader | data-input", "upstream | writer | data-output" })
	@Order(8)
	void readersAreDownstreamWritersUpstreamAndBothInBoth(String direction, String roles, String kinds) {
		Orders order = order();
		JsonNode result = ok(this.franz, body(order.object).put("direction", direction));
		JsonNode task = result.path("nodes").get(0);
		assertThat(result.path("nodes")).hasSize(1);
		assertThat(task.path("name").asString()).isEqualTo("Review order");
		assertThat(task.path("depth").asInt()).isEqualTo(1);
		assertThat(task.path("roles").valueStream().map(JsonNode::asString)).containsExactlyInAnyOrder(roles.split(","));
		assertThat(result.path("edges").valueStream().map((edge) -> edge.path("kind").asString()))
			.containsExactlyInAnyOrder(kinds.split(","));
		for (JsonNode edge : result.path("edges")) {
			if ("data-output".equals(edge.path("kind").asString())) {
				assertThat(edge.path("transformationType").asString()).isEqualTo("enrich");
				assertThat(edge.path("transformationRestricted").asBoolean()).isFalse();
				assertThat(edge.path("sourceId").asInt()).isEqualTo(order.task);
				assertThat(edge.path("targetId").asInt()).isEqualTo(order.object);
			}
			else {
				assertThat(edge.path("sourceId").asInt()).isEqualTo(order.object);
				assertThat(edge.path("targetId").asInt()).isEqualTo(order.task);
				assertThat(edge.path("transformationType").isNull()).isTrue();
			}
		}
	}

	/** the part that is unset, the roles of the task that remain, the kinds of the edges */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "data_input_metatype_id | writer | data-output",
			"data_output_metatype_id | reader | data-input" })
	@Order(9)
	void withoutADataInputPartNoReadersAndWithoutADataOutputPartNoWritersAreReturned(String column, String roles,
			String kinds) {
		Orders order = order();
		String saved = this.jdbc.queryForObject("SELECT " + column + "::text FROM lineage_model", String.class);
		this.jdbc.update("UPDATE lineage_model SET " + column + " = NULL");
		try {
			JsonNode result = ok(this.franz, body(order.object).put("direction", "both"));
			assertThat(result.path("nodes")).hasSize(1);
			assertThat(result.path("nodes").get(0).path("roles").valueStream().map(JsonNode::asString))
				.containsExactly(roles);
			assertThat(result.path("edges").valueStream().map((edge) -> edge.path("kind").asString()))
				.containsExactly(kinds);
		}
		finally {
			this.jdbc.update("UPDATE lineage_model SET " + column + " = ?::uuid", saved);
		}
	}

	@Test
	@Order(10)
	void aDeletedTaskIsLeftOut() {
		Orders order = order();
		delete(order.task);
		JsonNode result = ok(this.franz, body(order.object));
		assertThat(result.path("nodes")).isEmpty();
		assertThat(result.path("edges")).isEmpty();
	}

	// ── from a system ────────────────────────────────────────────────────────

	@Test
	@Order(11)
	void aSystemIsFollowedDownstreamAndItsEdgesNameTheCarriedDataObjects() {
		Systems systems = systems();
		JsonNode result = ok(this.franz, body(systems.sap).put("direction", "downstream"));
		assertThat(describeDepths(result)).containsExactlyInAnyOrder("DWH:1", "BI Tool:2");
		assertThat(names(result)).doesNotContain("SAP ERP");
		for (JsonNode edge : result.path("edges")) {
			assertThat(edge.path("carriedBy").valueStream().map((carried) -> carried.path("dataObjectName").asString()))
				.contains("Customer Master");
		}
		JsonNode second = edgeOf(result, systems.second);
		assertThat(second.path("carriedBy").valueStream().map((carried) -> carried.path("dataObjectName").asString()))
			.containsExactlyInAnyOrder("Customer Master", "Invoice");
		assertThat(paths(result)).containsExactly("SAP ERP>DWH>BI Tool");
	}

	@Test
	@Order(12)
	void aSystemIsFollowedUpstreamForOneDataObject() {
		Systems systems = systems();
		JsonNode all = ok(this.franz, body(systems.bi).put("direction", "upstream"));
		assertThat(names(all)).containsExactlyInAnyOrder("DWH", "SAP ERP", "CRM");
		JsonNode restricted = ok(this.franz,
				body(systems.bi).put("direction", "upstream").put("dataObjectId", systems.customerMaster));
		assertThat(names(restricted)).containsExactlyInAnyOrder("DWH", "SAP ERP");
		assertThat(paths(restricted)).containsExactly("BI Tool>DWH>SAP ERP");
	}

	@Test
	@Order(13)
	void bothIsTheUnionOfTheTwoWalksAndDoesNotTurnAround() {
		Systems systems = systems();
		int crm = systems.crm;
		int sap = systems.sap;
		carryingFlow(sap, crm, systems.customerMaster);
		JsonNode result = ok(this.franz, body(systems.dwh).put("direction", "both"));
		assertThat(describeDepths(result)).containsExactlyInAnyOrder("SAP ERP:1", "BI Tool:1");
		assertThat(names(result)).doesNotContain("CRM");
		JsonNode down = ok(this.franz, body(systems.dwh).put("direction", "downstream"));
		JsonNode up = ok(this.franz, body(systems.dwh).put("direction", "upstream"));
		List<String> union = new ArrayList<>(names(down));
		union.addAll(names(up));
		assertThat(names(result)).containsExactlyInAnyOrderElementsOf(union);
	}

	@Test
	@Order(14)
	void impactEqualsDownstreamWithImpactOnEveryNode() {
		Systems systems = systems();
		JsonNode down = ok(this.franz, body(systems.sap).put("direction", "downstream"));
		JsonNode impact = ok(this.franz, body(systems.sap).put("direction", "impact"));
		assertThat(describeDepths(impact)).containsExactlyInAnyOrderElementsOf(describeDepths(down));
		assertThat(impact.path("edges")).hasSameSizeAs(down.path("edges"));
		assertThat(impact.path("nodes")).isNotEmpty();
		for (JsonNode node : impact.path("nodes")) {
			assertThat(node.path("impact").asBoolean()).isTrue();
		}
		for (JsonNode node : down.path("nodes")) {
			assertThat(node.path("impact").asBoolean()).isFalse();
		}
	}

	/** the number of carried data objects, the number listed, whether it is truncated */
	@ParameterizedTest
	@CsvSource({ "1, 1, false", "50, 50, false", "51, 50, true" })
	@Order(15)
	void anEdgeNamesAtMost50CarriedDataObjects(int carried, int listed, boolean truncated) {
		int source = entity("Application Component", "Source " + carried);
		int target = entity("Application Component", "Target " + carried);
		int flow = this.api.connection(this.anna, type("Data Flow"), source, target);
		bulkCarried(flow, carried);
		JsonNode result = ok(this.franz, body(source).put("direction", "downstream"));
		JsonNode edge = result.path("edges").get(0);
		assertThat(edge.path("carriedBy")).hasSize(listed);
		assertThat(edge.path("carriedByTruncated").asBoolean()).isEqualTo(truncated);
		// by connection ID
		List<Integer> ids = edge.path("carriedBy").valueStream().map((entry) -> entry.path("connectionId").asInt()).toList();
		assertThat(ids).isSorted();
	}

	// ── depth and limits ──────────────────────────────────────────────────────

	/** maxDepth, the nodes returned downstream from SAP ERP */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "1 | DWH", "2 | DWH,BI Tool", "100 | DWH,BI Tool", "- | DWH,BI Tool" })
	@Order(16)
	void maxDepthLimitsTheWalkFromASystemAndDefaultsTo50(String depth, String expected) {
		Systems systems = systems();
		ObjectNode request = body(systems.sap).put("direction", "downstream");
		if (!"-".equals(depth)) {
			request.put("maxDepth", Integer.parseInt(depth));
		}
		assertThat(names(ok(this.franz, request))).containsExactlyInAnyOrder(expected.split(","));
	}

	/** maxDepth, the nodes returned downstream from a data object, where the first two systems are the seeds */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "1 | SAP ERP,DWH", "100 | SAP ERP,DWH,BI Tool", "- | SAP ERP,DWH,BI Tool" })
	@Order(17)
	void maxDepthLimitsTheWalkFromADataObject(String depth, String expected) {
		Chain chain = chain();
		ObjectNode request = body(chain.object).put("direction", "downstream");
		if (!"-".equals(depth)) {
			request.put("maxDepth", Integer.parseInt(depth));
		}
		assertThat(names(ok(this.franz, request))).containsExactlyInAnyOrder(expected.split(","));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 101, -1 })
	@Order(18)
	void aMaxDepthOutsideOneToHundredGives400(int depth) {
		Chain chain = chain();
		MvcTestResult result = this.api.post(QUERY, body(chain.object).put("maxDepth", depth), this.franz);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(400);
	}

	@Test
	@Order(19)
	void theDefaultDepthOf50ReturnsFiftyOfAChainOf51Hops() {
		int hops = 51;
		List<Integer> systems = IntStream.rangeClosed(0, hops)
			.mapToObj((index) -> sqlEntity("Application Component", "Chain " + index))
			.toList();
		for (int index = 0; index < hops; index++) {
			sqlConnection("Data Flow", systems.get(index), systems.get(index + 1));
		}
		ObjectNode request = body(systems.get(0)).put("direction", "downstream");
		JsonNode byDefault = ok(this.franz, request);
		assertThat(byDefault.path("nodes")).hasSize(50);
		assertThat(byDefault.path("truncated").asBoolean()).isFalse();
		JsonNode deepest = ok(this.franz, request.put("maxDepth", 100));
		assertThat(deepest.path("nodes")).hasSize(51);
		JsonNode one = ok(this.franz, request.put("maxDepth", 49));
		assertThat(one.path("nodes")).hasSize(49);
	}

	/** the number of nodes, the number returned, whether truncated */
	@ParameterizedTest
	@CsvSource({ "5000, 5000, false", "5001, 5000, true", "4999, 4999, false" })
	@Order(20)
	void atMost5000NodesAreReturned(int nodes, int returned, boolean truncated) {
		int source = entity("Application Component", "Hub " + nodes);
		bulkTargets(source, nodes);
		JsonNode result = ok(this.franz, body(source).put("direction", "downstream"));
		assertThat(result.path("nodes")).hasSize(returned);
		assertThat(result.path("truncated").asBoolean()).isEqualTo(truncated);
		assertThat(result.path("edges")).hasSizeLessThanOrEqualTo(returned);
	}

	/** the number of flows between two systems, the number of edges returned, whether truncated */
	@ParameterizedTest
	@CsvSource({ "10000, 10000, false", "10001, 10000, true", "9999, 9999, false" })
	@Order(21)
	void atMost10000EdgesAreReturned(int flows, int returned, boolean truncated) {
		int source = entity("Application Component", "Left " + flows);
		int target = entity("Application Component", "Right " + flows);
		bulkParallelFlows(source, target, flows);
		JsonNode result = ok(this.franz, body(source).put("direction", "downstream"));
		assertThat(result.path("edges")).hasSize(returned);
		assertThat(result.path("nodes")).hasSize(1);
		assertThat(result.path("truncated").asBoolean()).isEqualTo(truncated);
	}

	// ── the filter ───────────────────────────────────────────────────────────

	@Test
	@Order(22)
	void theMetatypeFilterIncludesSubtypesAndKeepsTheEdges() {
		Chain chain = chain();
		String warehouse = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, base_metatype_id)
				VALUES (?::uuid, 'Warehouse ' || gen_random_uuid(), false, ?::uuid) RETURNING id::text""", String.class,
				INSTANCE, type("Application Component"));
		this.jdbc.update("UPDATE entities SET metatype_id = ?::uuid WHERE id = ?", warehouse, chain.dwh);
		JsonNode result = ok(this.franz, body(chain.object).put("direction", "downstream")
			.set("metatypeIds", Api.object().arrayNode().add(type("Application Component"))));
		assertThat(names(result)).containsExactlyInAnyOrder("SAP ERP", "DWH");
		assertThat(result.path("edges")).hasSize(2);
		assertThat(paths(result)).containsExactly("SAP ERP>DWH");
		// BI Tool, outside the filter, still names an edge
		assertThat(result.path("edges").valueStream().map((edge) -> edge.path("targetId").asInt())).contains(chain.bi);
	}

	/** the number of metatypes in the filter, the expected status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "50, 200", "51, 400" })
	@Order(23)
	void theMetatypeFilterTakesAtMost50Metatypes(int count, int expected) {
		Chain chain = chain();
		this.jdbc.update("""
				INSERT INTO metatypes (config_id, name, is_connection)
				SELECT ?::uuid, 'Filler ' || gen_random_uuid(), false FROM generate_series(1, 60)""", INSTANCE);
		ArrayNode filter = Api.object().arrayNode();
		this.jdbc.queryForList("SELECT id::text FROM metatypes ORDER BY name, id LIMIT ?", String.class, count)
			.forEach(filter::add);
		assertThat(filter).hasSize(count);
		MvcTestResult result = this.api.post(QUERY, body(chain.object).set("metatypeIds", filter), this.franz);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
	}

	// ── errors and access ────────────────────────────────────────────────────

	@Test
	@Order(24)
	void anUnknownMetatypeInTheFilterGives422NamingTheFilter() {
		Chain chain = chain();
		MvcTestResult result = this.api.post(QUERY,
				body(chain.object).set("metatypeIds", Api.object().arrayNode().add(Api.id())), this.franz);
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains("metatypeIds");
	}

	@Test
	@Order(25)
	void aMissingStartGives400() {
		MvcTestResult result = this.api.post(QUERY, Api.object().put("direction", "both"), this.franz);
		assertThat(Api.status(result)).isEqualTo(400);
	}

	@Test
	@Order(26)
	void aDataObjectWithADataObjectStartGives400AndAnEntityThatIsNoDataObjectGives422() {
		Systems systems = systems();
		MvcTestResult withObject = this.api.post(QUERY, body(systems.customerMaster).put("dataObjectId", systems.invoice),
				this.franz);
		assertThat(Api.status(withObject)).isEqualTo(400);
		assertThat(Api.body(withObject).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("dataObjectId");
		MvcTestResult notAnObject = this.api.post(QUERY, body(systems.sap).put("dataObjectId", systems.dwh), this.franz);
		assertThat(Api.status(notAnObject)).isEqualTo(422);
		assertThat(Api.body(notAnObject).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("dataObjectId");
		delete(systems.invoice);
		MvcTestResult deleted = this.api.post(QUERY, body(systems.sap).put("dataObjectId", systems.invoice), this.franz);
		assertThat(Api.status(deleted)).isEqualTo(422);
		MvcTestResult unknown = this.api.post(QUERY, body(systems.sap).put("dataObjectId", 2_000_000_000), this.franz);
		assertThat(Api.status(unknown)).isEqualTo(422);
	}

	@Test
	@Order(27)
	void aPersonWithoutEntityReadGets403() {
		Chain chain = chain();
		TestSession none = this.principals.personWithoutRole();
		assertThat(Api.status(this.api.post(QUERY, body(chain.object), none))).isEqualTo(403);
	}

	@Test
	@Order(28)
	void anUnknownOrDeletedStartGives404() {
		Chain chain = chain();
		assertThat(Api.status(this.api.post(QUERY, body(2_000_000_000), this.franz))).isEqualTo(404);
		delete(chain.sap);
		assertThat(Api.status(this.api.post(QUERY, body(chain.sap), this.franz))).isEqualTo(404);
	}

	@Test
	@Order(29)
	void aConnectionAsStartGives422NamingTheStart() {
		Chain chain = chain();
		MvcTestResult result = this.api.post(QUERY, body(chain.first), this.franz);
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("startEntityId");
	}

	/** the part that is unset */
	@ParameterizedTest
	@CsvSource({ "data_object_metatype_id", "data_flow_metatype_id", "carries_data_metatype_id" })
	@Order(30)
	void anUnsetDataObjectDataFlowOrCarriesDataPartGives422LineageModelIncomplete(String column) {
		Chain chain = chain();
		String saved = this.jdbc.queryForObject("SELECT " + column + "::text FROM lineage_model", String.class);
		this.jdbc.update("UPDATE lineage_model SET " + column + " = NULL");
		try {
			MvcTestResult result = this.api.post(QUERY, body(chain.object), this.franz);
			assertThat(Api.status(result)).isEqualTo(422);
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
				.contains("lineageModel");
			assertThat(Api.content(result)).contains("LINEAGE_MODEL_INCOMPLETE");
		}
		finally {
			this.jdbc.update("UPDATE lineage_model SET " + column + " = ?::uuid", saved);
		}
	}

	// ── property visibility of the transformation type ───────────────────────

	@Test
	@Order(31)
	void aRestrictedTransformationTypeIsWithheldAlsoWhenNoValueIsSetAndAnOverrideIsAudited() {
		Orders order = order();
		// a second output without a value
		int other = entity("User Task", "Check order");
		int bare = sqlConnection("Data Output Association", other, order.object);
		String property = this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = 'Transformation type'",
				String.class);
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?::uuid", property);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?::uuid, 'enterprise-architect', 'read'), (?::uuid, 'enterprise-architect', 'write')""", property, property);
		try {
			// an enterprise architect reads the value
			JsonNode architect = ok(this.anna, body(order.object).put("direction", "upstream"));
			assertThat(edgeOf(architect, order.output).path("transformationType").asString()).isEqualTo("enrich");
			assertThat(edgeOf(architect, order.output).path("transformationRestricted").asBoolean()).isFalse();
			// a viewer gets none, with and without a stored value
			JsonNode viewer = ok(this.franz, body(order.object).put("direction", "upstream"));
			for (int output : List.of(order.output, bare)) {
				JsonNode edge = edgeOf(viewer, output);
				assertThat(edge.path("transformationType").isNull()).as("value of " + output).isTrue();
				assertThat(edge.path("transformationRestricted").asBoolean()).as("restricted " + output).isTrue();
			}
			// a read through the override writes one event naming the property and the connection
			String role = "lineage-override-" + Api.id();
			this.jdbc.update("INSERT INTO authorization_roles (id, name, overrides_property_visibility) VALUES (?, ?, true)",
					role, role);
			TestSession overrider = this.principals.personWithIdpRoles("viewer", role);
			int before = overrideEvents();
			JsonNode overridden = ok(overrider, body(order.object).put("direction", "upstream"));
			assertThat(edgeOf(overridden, order.output).path("transformationType").asString()).isEqualTo("enrich");
			assertThat(overrideEvents()).isEqualTo(before + 1);
			JsonNode details = JsonMapper.shared().readTree(this.jdbc.queryForObject(
					"SELECT details::text FROM audit.events WHERE event_type = 'property-override-access' ORDER BY id DESC LIMIT 1",
					String.class));
			assertThat(details.path("channel").asString()).isEqualTo("lineage");
			assertThat(details.path("properties").get(0).path("property").asString()).isEqualTo("Transformation type");
			assertThat(details.path("properties").get(0).path("entityIds").valueStream().map(JsonNode::asInt))
				.containsExactly(order.output);
			// a read without the override writes none
			ok(this.anna, body(order.object).put("direction", "upstream"));
			ok(this.franz, body(order.object).put("direction", "upstream"));
			assertThat(overrideEvents()).isEqualTo(before + 1);
		}
		finally {
			this.jdbc.update("DELETE FROM property_visibility_roles WHERE property_def_id = ?::uuid", property);
			this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'public' WHERE id = ?::uuid", property);
		}
	}

	// ── fixtures ─────────────────────────────────────────────────────────────

	/** SAP ERP to DWH to BI Tool, each flow carrying "Customer Master". */
	private record Chain(int object, int sap, int dwh, int bi, int first, int second, int firstCarries,
			int secondCarries) {
	}

	private Chain chain() {
		int object = entity("Data Object", "Customer Master");
		int sap = entity("Application Component", "SAP ERP");
		int dwh = entity("Application Component", "DWH");
		int bi = entity("System Software", "BI Tool");
		int first = flow(sap, dwh);
		int firstCarries = carries(first, object);
		int second = flow(dwh, bi);
		int secondCarries = carries(second, object);
		return new Chain(object, sap, dwh, bi, first, second, firstCarries, secondCarries);
	}

	/** The chain for a system start: second flow carries two objects, CRM sends "Invoice" to BI Tool. */
	private record Systems(int customerMaster, int invoice, int sap, int dwh, int bi, int crm, int first, int second,
			int third) {
	}

	private Systems systems() {
		int customerMaster = entity("Data Object", "Customer Master");
		int invoice = entity("Data Object", "Invoice");
		int sap = entity("Application Component", "SAP ERP");
		int dwh = entity("Application Component", "DWH");
		int bi = entity("System Software", "BI Tool");
		int crm = entity("Application Component", "CRM");
		int first = carryingFlow(sap, dwh, customerMaster);
		int second = carryingFlow(dwh, bi, customerMaster, invoice);
		int third = carryingFlow(crm, bi, invoice);
		return new Systems(customerMaster, invoice, sap, dwh, bi, crm, first, second, third);
	}

	/** "Order" read and enriched by the task "Review order". */
	private record Orders(int object, int task, int input, int output) {
	}

	private Orders order() {
		int object = entity("Data Object", "Order");
		int task = entity("User Task", "Review order");
		int input = sqlConnection("Data Input Association", object, task);
		int output = sqlConnection("Data Output Association", task, object);
		this.jdbc.update("""
				INSERT INTO entity_property_values (entity_id, property_def_id, value_enum)
				SELECT ?, id, 'enrich' FROM property_definitions WHERE name = 'Transformation type'""", output);
		return new Orders(object, task, input, output);
	}

	/** The fixtures are written with SQL: the checks of the write are tested with the packages, and the rate limit counts every request. */
	private int entity(String type, String name) {
		return sqlEntity(type, name);
	}

	private int flow(int from, int to) {
		return sqlConnection("Data Flow", from, to);
	}

	private int carries(int flow, int object) {
		return sqlConnection("Carries Data", flow, object);
	}

	private int carryingFlow(int from, int to, int... objects) {
		int flow = flow(from, to);
		for (int object : objects) {
			carries(flow, object);
		}
		return flow;
	}

	private String type(String name) {
		return types.computeIfAbsent(name,
				(key) -> this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ?", String.class, key));
	}

	private void delete(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE id = ?",
				id);
	}

	private int sqlEntity(String type, String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?::uuid, ?, (SELECT min(id) FROM persons))
				RETURNING id""", Integer.class, type(type), name);
	}

	private int sqlConnection(String type, int source, int target) {
		int id = sqlEntity(type, "Link " + source + "-" + target);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	/** {@code count} data objects, each carried by the flow. */
	private void bulkCarried(int flow, int count) {
		this.jdbc.update("""
				WITH objects AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?::uuid, 'Object ' || g, (SELECT min(id) FROM persons) FROM generate_series(1, ?) g RETURNING id),
				carriers AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?::uuid, 'Carries ' || g, (SELECT min(id) FROM persons) FROM generate_series(1, ?) g RETURNING id),
				paired AS (
				    SELECT o.id AS object_id, c.id AS carrier_id FROM
				        (SELECT id, row_number() OVER (ORDER BY id) AS position FROM objects) o
				        JOIN (SELECT id, row_number() OVER (ORDER BY id) AS position FROM carriers) c USING (position))
				INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id)
				SELECT carrier_id, ?, object_id FROM paired""", type("Data Object"), count, type("Carries Data"), count,
				flow);
	}

	/** {@code count} systems, each the target of a flow from the source. */
	private void bulkTargets(int source, int count) {
		this.jdbc.update("""
				WITH targets AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?::uuid, 'Target ' || g, (SELECT min(id) FROM persons) FROM generate_series(1, ?) g RETURNING id),
				flows AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?::uuid, 'Flow ' || g, (SELECT min(id) FROM persons) FROM generate_series(1, ?) g RETURNING id),
				paired AS (
				    SELECT t.id AS target_id, f.id AS flow_id FROM
				        (SELECT id, row_number() OVER (ORDER BY id) AS position FROM targets) t
				        JOIN (SELECT id, row_number() OVER (ORDER BY id) AS position FROM flows) f USING (position))
				INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id)
				SELECT flow_id, ?, target_id FROM paired""", type("Application Component"), count, type("Data Flow"), count,
				source);
	}

	/** {@code count} flows from the source to the target. */
	private void bulkParallelFlows(int source, int target, int count) {
		this.jdbc.update("""
				WITH flows AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?::uuid, 'Parallel ' || g, (SELECT min(id) FROM persons) FROM generate_series(1, ?) g RETURNING id)
				INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id)
				SELECT id, ?, ? FROM flows""", type("Data Flow"), count, source, target);
	}

	private int overrideEvents() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'property-override-access'",
				Integer.class);
	}

	// ── reading the result ───────────────────────────────────────────────────

	private static ObjectNode body(int start) {
		return Api.object().put("startEntityId", start);
	}

	private JsonNode ok(TestSession session, ObjectNode request) {
		return this.api.ok(this.api.post(QUERY, request, session));
	}

	private static List<String> names(JsonNode result) {
		return result.path("nodes").valueStream().map((node) -> node.path("name").asString()).toList();
	}

	/** name:depth */
	private static List<String> describeDepths(JsonNode result) {
		return result.path("nodes")
			.valueStream()
			.map((node) -> node.path("name").asString() + ":" + node.path("depth").asInt())
			.toList();
	}

	/** name:depth:roles with the roles joined by comma */
	private static List<String> describe(JsonNode result) {
		return result.path("nodes")
			.valueStream()
			.map((node) -> node.path("name").asString() + ":" + node.path("depth").asInt() + ":"
					+ String.join(",", node.path("roles").valueStream().map(JsonNode::asString).toList()))
			.toList();
	}

	/** The paths as names joined by ">". */
	private static List<String> paths(JsonNode result) {
		Map<Integer, String> names = new LinkedHashMap<>();
		names.put(result.path("start").path("id").asInt(), result.path("start").path("name").asString());
		result.path("nodes").forEach((node) -> names.put(node.path("id").asInt(), node.path("name").asString()));
		return result.path("paths")
			.valueStream()
			.map((path) -> String.join(">", path.valueStream().map((id) -> names.getOrDefault(id.asInt(), "?" + id.asInt())).toList()))
			.toList();
	}

	private static JsonNode edgeOf(JsonNode result, int id) {
		return result.path("edges")
			.valueStream()
			.filter((edge) -> edge.path("id").asInt() == id)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no edge " + id + " in " + result.path("edges")));
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-lineage-query").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
