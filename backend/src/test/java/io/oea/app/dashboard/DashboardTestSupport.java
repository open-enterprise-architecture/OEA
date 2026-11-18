package io.oea.app.dashboard;

import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Requests, bodies and repository content shared by the dashboard tests (us-055-dashboards). */
final class DashboardTestSupport {

	static final String DASHBOARDS = "/api/v1/dashboards";

	static final String VIEWS = "/api/v1/views/dashboards";

	static final String CATALOGS = "/api/v1/catalogs";

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private UUID metatype;

	DashboardTestSupport(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	/** A response: status, parsed body (or null without one) and the {@code ETag}. */
	record Reply(int status, JsonNode body, String etag, String raw) {

		JsonNode json() {
			if (this.body == null) {
				throw new AssertionError("no body: " + this.status);
			}
			return this.body;
		}

		/** The version of a dashboard in the body. */
		long version() {
			return json().path("version").asLong();
		}

		/** The ID of a dashboard in the body. */
		String id() {
			return json().path("id").asString();
		}

		/** The fields named by an {@code invalidFields} entry. */
		List<String> invalidFields() {
			List<String> fields = new ArrayList<>();
			json().path("invalidFields").forEach((field) -> fields.add(field.asString()));
			return fields;
		}

		/** Whether a rejected field is {@code name} or a nested path ending in it. */
		boolean names(String name) {
			return invalidFields().stream().anyMatch((field) -> field.equals(name) || field.endsWith("." + name));
		}

	}

	Reply call(HttpMethod method, String path, TestSession session, Object body, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		MvcTestResult result = request.exchange();
		String raw;
		try {
			raw = result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
		JsonNode json = (raw.isEmpty() || !raw.startsWith("{") && !raw.startsWith("[")) ? null
				: JsonMapper.shared().readTree(raw);
		return new Reply(result.getResponse().getStatus(), json, result.getResponse().getHeader("ETag"), raw);
	}

	Reply get(String path, TestSession session) {
		return call(HttpMethod.GET, path, session, null, null);
	}

	/** A write with {@code If-Match} of the given version. */
	Reply write(HttpMethod method, String path, TestSession session, Object body, long version) {
		return call(method, path, session, body, "\"" + version + "\"");
	}

	Reply create(TestSession session, String name, String scope) {
		return call(HttpMethod.POST, DASHBOARDS, session, dashboard(name, scope), null);
	}

	/** Creates the dashboard and expects 201. */
	Reply created(TestSession session, String name, String scope) {
		Reply reply = create(session, name, scope);
		if (reply.status() != 201) {
			throw new AssertionError("create " + name + ": " + reply.status() + " " + reply.raw());
		}
		return reply;
	}

	static ObjectNode dashboard(String name, String scope) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("description", "").put("scope", scope);
	}

	static ObjectNode position(int col, int row, int width, int height) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("col", col)
			.put("row", row)
			.put("width", width)
			.put("height", height);
	}

	static ObjectNode textWidget(String title, String content, ObjectNode position) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("type", "text").put("title", title);
		body.set("position", position);
		body.putObject("config").put("content", content);
		return body;
	}

	static ObjectNode tableWidget(String title, String catalogId, String savedViewId, Integer pageSize,
			ObjectNode position) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("type", "table").put("title", title);
		body.set("position", position);
		ObjectNode config = body.putObject("config").put("catalogId", catalogId);
		if (savedViewId != null) {
			config.put("savedViewId", savedViewId);
		}
		if (pageSize != null) {
			config.put("pageSize", pageSize);
		}
		return body;
	}

	/** Adds a widget with the current version and expects 200; returns the new dashboard. */
	Reply addWidget(TestSession session, Reply dashboard, ObjectNode widget) {
		Reply reply = write(HttpMethod.POST, DASHBOARDS + "/" + dashboard.id() + "/widgets", session, widget,
				dashboard.version());
		if (reply.status() != 200) {
			throw new AssertionError("add widget: " + reply.status() + " " + reply.raw());
		}
		return reply;
	}

	/** The ID of the widget with the title in a dashboard body. */
	static String widgetId(Reply dashboard, String title) {
		for (JsonNode widget : dashboard.json().path("widgets")) {
			if (widget.path("title").asString().equals(title)) {
				return widget.path("id").asString();
			}
		}
		throw new AssertionError("no widget " + title);
	}

	/** Creates a catalog and returns its ID. */
	String catalog(TestSession session, String name, String scope) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", metatype().toString())
			.put("scope", scope);
		Reply reply = call(HttpMethod.POST, CATALOGS, session, body, null);
		if (reply.status() != 201) {
			throw new AssertionError("catalog " + name + ": " + reply.status() + " " + reply.raw());
		}
		return reply.id();
	}

	/** Saves a view of the catalog (the column {@code name}) and returns its ID. */
	String view(TestSession session, String catalogId, String name) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("isDefault", false);
		ArrayNode columns = body.putArray("columnOrder");
		columns.add("name");
		Reply reply = call(HttpMethod.POST, CATALOGS + "/" + catalogId + "/views", session, body, null);
		if (reply.status() != 201) {
			throw new AssertionError("view " + name + ": " + reply.status() + " " + reply.raw());
		}
		for (JsonNode view : reply.json().path("savedViews")) {
			if (view.path("name").asString().equals(name)) {
				return view.path("id").asString();
			}
		}
		throw new AssertionError("no view " + name);
	}

	/** An element type that catalogs are based on, created once per database. */
	UUID metatype() {
		if (this.metatype == null) {
			this.metatype = this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
					RETURNING id""", UUID.class, "Dashboard Element " + UUID.randomUUID());
		}
		return this.metatype;
	}

	/** Creates elements of the catalog type, named {@code prefix 01}, {@code prefix 02} and so on. */
	void entities(String prefix, int count, long createdBy) {
		for (int i = 1; i <= count; i++) {
			this.jdbc.update("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?)", metatype(),
					"%s %02d".formatted(prefix, i), createdBy);
		}
	}

	long personOf(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	/** The number of audit events of the type for the dashboard. */
	int audited(String eventType, String dashboardId) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?", Integer.class, eventType,
				dashboardId);
	}

	/** The details of the audit events of the type for the dashboard, oldest first. */
	List<String> auditDetails(String eventType, String dashboardId) {
		return this.jdbc.queryForList(
				"SELECT details::text FROM audit.events WHERE event_type = ? AND target_id = ? ORDER BY id",
				String.class, eventType, dashboardId);
	}

	int count(String sql, Object... arguments) {
		return this.jdbc.queryForObject(sql, Integer.class, arguments);
	}

	static String tempDirectory(String prefix) {
		try {
			return Files.createTempDirectory(prefix).toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String repeat(int length) {
		return "n".repeat(length);
	}

}
