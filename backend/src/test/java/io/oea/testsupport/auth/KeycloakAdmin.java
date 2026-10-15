package io.oea.testsupport.auth;

import io.oea.testsupport.containers.OeaKeycloak;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/**
 * IdP-side state changes through the Keycloak Admin REST API (ADR-036 §5): group membership
 * and session revocation of development users. Tests restore what they change.
 */
public final class KeycloakAdmin {

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private KeycloakAdmin() {
	}

	public static void removeFromGroup(String username, String groupName) {
		send("DELETE", "/users/" + DevRealm.subject(username) + "/groups/" + DevRealm.groupId(groupName));
	}

	public static void addToGroup(String username, String groupName) {
		send("PUT", "/users/" + DevRealm.subject(username) + "/groups/" + DevRealm.groupId(groupName));
	}

	/** Ends all IdP sessions of the user; their refresh tokens become invalid. */
	public static void logout(String username) {
		send("POST", "/users/" + DevRealm.subject(username) + "/logout");
	}

	private static void send(String method, String path) {
		String base = OeaKeycloak.issuer().replace("/realms/" + OeaKeycloak.REALM, "");
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/admin/realms/" + OeaKeycloak.REALM + path))
			.header("Authorization", "Bearer " + token(base))
			.method(method, HttpRequest.BodyPublishers.noBody())
			.build();
		HttpResponse<String> response = call(request);
		if (response.statusCode() / 100 != 2) {
			throw new IllegalStateException(method + " " + path + " failed (HTTP " + response.statusCode() + ")");
		}
	}

	private static String token(String base) {
		String form = "grant_type=password&client_id=admin-cli&username="
				+ URLEncoder.encode(OeaKeycloak.ADMIN_USER, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(OeaKeycloak.ADMIN_PASSWORD, StandardCharsets.UTF_8);
		HttpResponse<String> response = call(HttpRequest
			.newBuilder(URI.create(base + "/realms/master/protocol/openid-connect/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Admin token request failed (HTTP " + response.statusCode() + ")");
		}
		return JsonMapper.shared().readTree(response.body()).path("access_token").asString();
	}

	private static HttpResponse<String> call(HttpRequest request) {
		try {
			return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Keycloak Admin API not reachable", ex);
		}
	}

}
