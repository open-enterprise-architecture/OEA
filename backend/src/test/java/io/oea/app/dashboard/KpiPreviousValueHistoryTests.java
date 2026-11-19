package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.entity.EntityAggregationStore;
import io.oea.core.entity.EntityAggregationStore.GroupRow;
import io.oea.core.entity.EntityStore;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.OffsetClock;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Design D2 of us-056-kpi-previous-value end to end: the history that the real write path leaves
 * (the entity batch for creating and changing values, {@link EntityStore#softDelete} for the
 * deletion) is read correctly by {@code aggregateAt} and by the KPI, with a clock that is
 * advanced between the writes. Version 1 writes no history, each later version the old value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KpiPreviousValueHistoryTests.ClockConfiguration.class)
class KpiPreviousValueHistoryTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-kpi-previous-history")
			+ "/setup-token";

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

	@Autowired
	private OffsetClock clock;

	@Autowired
	private EntityAggregationStore store;

	@Autowired
	private EntityStore entities;

	private ObjectNode entity(UUID type, String name, UUID property, String value) {
		ObjectNode entity = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("metatypeId", type.toString())
			.put("externalId", name);
		if (property != null) {
			entity.putArray("properties").addObject().put("propertyDefId", property.toString()).put("value", value);
		}
		return entity;
	}

	private void write(TestSession session, ObjectNode... entities) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("strategy", "upsert");
		ArrayNode list = body.putArray("entities");
		for (ObjectNode entity : entities) {
			list.add(entity);
		}
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
		assertThat(result.getResponse().getStatus()).as(content(result)).isEqualTo(200);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString() + result.getResponse().getHeader("WWW-Authenticate");
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A session whose times are those of the (advanced) clock, since the test helper uses the database time. */
	private TestSession session(TestPrincipals principals, String role) {
		TestSession session = principals.personWithIdpRole(role);
		Instant now = this.clock.instant();
		Timestamp later = Timestamp.from(now.plus(Duration.ofHours(12)));
		this.jdbc.update("UPDATE sessions SET authenticated_at = ?, last_activity_at = ?, absolute_expires_at = ?, "
				+ "idp_claims_evaluated_at = ? WHERE id = ?", Timestamp.from(now), Timestamp.from(now), later, later,
				session.sessionId());
		return session;
	}

	private Instant instant(String sql) {
		return this.jdbc.queryForObject(sql, Timestamp.class).toInstant();
	}

	private int id(String name) {
		return this.jdbc.queryForObject("SELECT id FROM entities WHERE external_id = ?", Integer.class, name);
	}

	private JsonNode kpi(Reply dashboard, String title, TestSession reader) {
		String id = DashboardTestSupport.widgetId(dashboard, title);
		Reply data = new DashboardTestSupport(this.mvc, this.jdbc)
			.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/widgets/" + id + "/data", reader);
		assertThat(data.status()).as(data.raw()).isEqualTo(200);
		return data.json();
	}

	@Test
	void theHistoryOfTheRealWritePathGivesTheValuesOfEarlierTimes() {
		DashboardTestSupport support = new DashboardTestSupport(this.mvc, this.jdbc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		TestSession kurt = session(principals, "enterprise-architect");
		long kurtId = support.personOf(kurt);
		UUID type = support.elementType("Application", null, null);
		UUID cost = support.property("Cost", "integer", "public");
		support.map(type, cost);
		// the dashboard is set up first; each advance of the clock needs a new session
		Reply dashboard = support.created(kurt, "History " + UUID.randomUUID().toString().substring(0, 8), "instance");
		for (String[] widget : new String[][] { { "Sum", "sum" }, { "Count", "count" } }) {
			ObjectNode aggregation = DashboardTestSupport.aggregation(type, cost, widget[1], null);
			ObjectNode body = DashboardTestSupport.kpiWidget(widget[0], aggregation,
					DashboardTestSupport.position("Sum".equals(widget[0]) ? 1 : 7, 1, 6, 2));
			DashboardTestSupport.config(body).put("comparison", "vs-previous-value");
			dashboard = support.addWidget(kurt, dashboard, body);
		}

		// t0: version 1 of three entities (no history)
		write(kurt, entity(type, "a", cost, "5"), entity(type, "b", cost, "10"), entity(type, "c", cost, "100"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_property_value_history", Integer.class))
			.isZero();
		Instant t0 = instant("SELECT max(created_at) FROM entities WHERE external_id IN ('a', 'b', 'c')");

		// t1: a 5 -> 7, b loses its value, c is deleted, d is created
		this.clock.advance(Duration.ofDays(1));
		Instant deletedAt = this.clock.instant();
		kurt = session(principals, "enterprise-architect");
		write(kurt, entity(type, "a", cost, "7"), entity(type, "b", cost, ""), entity(type, "d", cost, "50"));
		this.entities.softDelete(id("c"), kurtId, deletedAt, "test");
		Instant t1 = instant("SELECT greatest(max(changed_at), (SELECT created_at FROM entities WHERE external_id = 'd'), "
				+ "(SELECT deleted_at FROM entities WHERE external_id = 'c')) FROM entity_property_value_history");

		// t2: a 7 -> 9
		this.clock.advance(Duration.ofDays(1));
		kurt = session(principals, "enterprise-architect");
		write(kurt, entity(type, "a", cost, "9"));
		Instant t2 = instant("SELECT max(changed_at) FROM entity_property_value_history");

		assertThat(this.jdbc.queryForList("""
				SELECT e.external_id || ':' || h.entity_version || ':' || coalesce(h.value_int::text, '-')
				FROM entity_property_value_history h JOIN entities e ON e.id = h.entity_id
				ORDER BY e.external_id, h.entity_version""", String.class)).containsExactly("a:2:5", "a:3:7", "b:2:10");

		Set<UUID> types = Set.of(type);
		// before the creation, at the creation and between the writes
		assertThat(this.store.aggregateAt(types, cost, t0.minusSeconds(1)).count()).isZero();
		assertThat(this.store.aggregateAt(types, cost, t0)).isEqualTo(new GroupRow(null, null, 3, 115L, 5L, 100L));
		assertThat(this.store.aggregateAt(types, cost, t0.plus(Duration.ofHours(12))))
			.isEqualTo(new GroupRow(null, null, 3, 115L, 5L, 100L));
		// the changes of t1 have taken effect: a is 7, b has no value, c is deleted, d exists
		assertThat(this.store.aggregateAt(types, cost, t1)).isEqualTo(new GroupRow(null, null, 2, 57L, 7L, 50L));
		assertThat(this.store.aggregateAt(types, cost, t1.plus(Duration.ofHours(12))))
			.isEqualTo(new GroupRow(null, null, 2, 57L, 7L, 50L));
		assertThat(this.store.aggregateAt(types, null, t1.plus(Duration.ofHours(12))).count()).isEqualTo(3);
		// the change of t2 and after
		assertThat(this.store.aggregateAt(types, cost, t2)).isEqualTo(new GroupRow(null, null, 2, 59L, 9L, 50L));
		assertThat(this.store.aggregateAt(types, cost, t2.plusSeconds(1)))
			.isEqualTo(new GroupRow(null, null, 2, 59L, 9L, 50L));

		// the KPI 30 days after t0 plus 12 hours compares with the state between the writes
		this.clock.advance(Duration.ofHours(28 * 24 + 12));
		assertThat(this.clock.instant().minus(Duration.ofDays(30))).isAfter(t0).isBefore(t1);
		TestSession franz = session(principals, "viewer");
		JsonNode sum = kpi(dashboard, "Sum", franz);
		assertThat(sum.path("value").asDouble()).isEqualTo(59.0);
		assertThat(sum.path("previousValue").asDouble()).isEqualTo(115.0);
		JsonNode count = kpi(dashboard, "Count", franz);
		assertThat(count.path("value").asDouble()).isEqualTo(2.0);
		assertThat(count.path("previousValue").asDouble()).isEqualTo(3.0);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		OffsetClock testClock() {
			return new OffsetClock();
		}

	}

}
