package io.oea.app.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** HTTP calls of the integration tests of the organization model and the assignments. */
final class Api {

	private final MockMvcTester mvc;

	Api(MockMvcTester mvc) {
		this.mvc = mvc;
	}

	MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path.replace(" ", "%20")))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	MvcTestResult get(String path, TestSession session) {
		return call(HttpMethod.GET, path, null, session, null);
	}

	MvcTestResult post(String path, JsonNode body, TestSession session) {
		return call(HttpMethod.POST, path, body, session, null);
	}

	MvcTestResult put(String path, JsonNode body, TestSession session, String ifMatch) {
		return call(HttpMethod.PUT, path, body, session, ifMatch);
	}

	/** Writes an entity through the batch endpoint, atomic; 200 or 422. */
	MvcTestResult writeEntity(TestSession session, String metatypeId, String name, String propertyDefId, String value) {
		ObjectNode item = object().put("name", name).put("metatypeId", metatypeId);
		if (propertyDefId != null) {
			item.putArray("properties").add(object().put("propertyDefId", propertyDefId).put("value", value));
		}
		ObjectNode body = object().put("atomic", true);
		body.putArray("entities").add(item);
		return post("/api/v1/entities/batch", body, session);
	}

	/** Creates an entity; its ID. */
	int entity(TestSession session, String metatypeId, String name) {
		return ok(writeEntity(session, metatypeId, name, null, null)).path("items").get(0).path("id").asInt();
	}

	/** Writes a connection through the batch endpoint, atomic; 200 or 422. */
	MvcTestResult writeConnection(TestSession session, String metatypeId, int source, int target) {
		ObjectNode body = object().put("atomic", true);
		body.putArray("connections")
			.add(object().put("connectionMetatypeId", metatypeId).put("sourceId", source).put("targetId", target));
		return post("/api/v1/entities/batch", body, session);
	}

	/** Creates a connection; its ID. */
	int connection(TestSession session, String metatypeId, int source, int target) {
		return ok(writeConnection(session, metatypeId, source, target)).path("items").get(0).path("id").asInt();
	}

	JsonNode ok(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	JsonNode created(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result);
	}

	static int status(MvcTestResult result) {
		return result.getResponse().getStatus();
	}

	static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static JsonNode body(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	static String etag(MvcTestResult result) {
		return result.getResponse().getHeader("ETag");
	}

	static String id() {
		return UUID.randomUUID().toString();
	}

	static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

}
