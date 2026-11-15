package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.login.AccessTokenCleanup;
import io.oea.app.login.AccessTokens;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Steps for features/identity/access-tokens.feature (s12-client-credentials-token); the instance
 * and the system admin come from {@link LocalLoginSteps}, the service accounts from
 * {@link ServiceAccountSteps} of the same scenario. Every token is registered for the log check
 * of REQ-175.
 */
public class AccessTokenSteps {

	private static final String TOKEN = "/api/v1/oauth2/token";

	private final LocalLoginSteps instance;

	private final ServiceAccountSteps accounts;

	private HttpResponse<String> response;

	private String token;

	private final List<HttpResponse<String>> refusals = new ArrayList<>();

	public AccessTokenSteps(LocalLoginSteps instance, ServiceAccountSteps accounts) {
		this.instance = instance;
		this.accounts = accounts;
	}

	// Token requests

	@When("{string} requests a token with its client ID and secret")
	public void request(String name) throws Exception {
		this.response = token(this.accounts.clientId(name), this.accounts.secret(name), "grant_type=client_credentials");
		remember();
	}

	@When("{string} requests a token with its previous secret")
	public void requestWithPrevious(String name) throws Exception {
		this.response = token(this.accounts.clientId(name), this.accounts.previousSecret(name),
				"grant_type=client_credentials");
	}

	@When("{string} requests a token with a wrong secret")
	public void requestWrong(String name) throws Exception {
		this.response = token(this.accounts.clientId(name), "oea_cs_wrong-" + UUID.randomUUID(),
				"grant_type=client_credentials");
	}

	@Given("{string} requested {int} tokens with a wrong secret")
	public void requestedWrong(String name, int count) throws Exception {
		for (int n = 0; n < count; n++) {
			requestWrong(name);
			assertThat(this.response.statusCode()).isEqualTo(401);
		}
	}

	@When("{string} requests a token with the grant type {string}")
	public void requestGrant(String name, String grantType) throws Exception {
		this.response = token(this.accounts.clientId(name), this.accounts.secret(name), "grant_type=" + grantType);
	}

	@When("{string} requests a token with its client ID and secret in the request body")
	public void requestInBody(String name) throws Exception {
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri(TOKEN))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id="
					+ this.accounts.clientId(name) + "&client_secret=" + encode(this.accounts.secret(name)))));
	}

	@When("{string} requests a token with its client ID and secret in the query string")
	public void requestInQuery(String name) throws Exception {
		this.response = this.instance.send(HttpRequest
			.newBuilder(this.instance.uri(TOKEN + "?client_id=" + this.accounts.clientId(name) + "&client_secret="
					+ encode(this.accounts.secret(name))))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")));
	}

	@Given("100 tokens were requested for unknown clients from one address")
	public void manyUnknown() throws Exception {
		for (int n = 0; n < 100; n++) {
			HttpResponse<String> refused = token(UUID.randomUUID().toString(), "oea_cs_unknown",
					"grant_type=client_credentials");
			assertThat(refused.statusCode()).isEqualTo(401);
		}
	}

	@When("tokens are requested for an unknown client, with a wrong secret of {string}, with the expired secret of {string} and for {string}")
	public void refusals(String wrong, String expired, String deactivated) throws Exception {
		this.refusals.add(token(UUID.randomUUID().toString(), "oea_cs_unknown", "grant_type=client_credentials"));
		this.refusals.add(token(this.accounts.clientId(wrong), "oea_cs_wrong", "grant_type=client_credentials"));
		this.refusals.add(token(this.accounts.clientId(expired), this.accounts.secret(expired),
				"grant_type=client_credentials"));
		this.refusals.add(token(this.accounts.clientId(deactivated), this.accounts.secret(deactivated),
				"grant_type=client_credentials"));
	}

	@Given("the secret of {string} expires in {int} seconds")
	public void secretExpiresIn(String name, int seconds) throws Exception {
		// the expiry must stay after the creation
		this.instance.superuser("UPDATE machine_credentials SET created_at = now() - interval '1 hour', expires_at = now() + interval '"
				+ seconds + " seconds' WHERE client_id = '" + this.accounts.clientId(name) + "' AND status = 'active'");
	}

	@Given("the secret of {string} expired")
	public void secretExpired(String name) throws Exception {
		secretExpiresIn(name, -60);
	}

	@Given("{string} holds a token")
	public void holds(String name) throws Exception {
		request(name);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("{string} holds a token issued {int} seconds ago")
	public void holdsIssuedAgo(String name, int seconds) throws Exception {
		holds(name);
		this.instance.superuser("UPDATE access_tokens SET issued_at = now() - interval '" + seconds
				+ " seconds', expires_at = now() - interval '" + seconds + " seconds' + interval '900 seconds' "
				+ "WHERE token_hash = decode('" + hash(this.token) + "', 'hex')");
	}

	@Given("{string} holds two tokens, expired 23 hours and 59 minutes ago and 24 hours ago")
	public void holdsTwoExpired(String name) throws Exception {
		holds(name);
		String recent = this.token;
		holds(name);
		String old = this.token;
		expire(recent, "23 hours 59 minutes");
		expire(old, "24 hours");
		this.kept = recent;
		this.deleted = old;
	}

	private String kept;

	private String deleted;

	@When("the token cleanup runs")
	public void cleanup() {
		this.instance.bean(AccessTokenCleanup.class).deleteExpired();
	}

	// Requests with a token

	@When("{string} writes the entity {string} with its token and without CSRF token")
	public void write(String name, String entity) throws Exception {
		String metatype = this.instance.single("""
				SELECT coalesce((SELECT id::text FROM metatypes WHERE name = 'Token Component'), '')""");
		if (metatype.isEmpty()) {
			metatype = this.instance.single("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Token Component')
					RETURNING id::text""");
		}
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/entities/batch"))
			.header("Authorization", "Bearer " + this.token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"entities\":[{\"name\":\"" + entity + "\",\"metatypeId\":\""
					+ metatype + "\",\"externalId\":\"token-" + entity + "\",\"properties\":[]}]}")));
	}

	@When("{string} reads the current principal with its token")
	public void readMe(String name) throws Exception {
		this.response = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me")).header("Authorization", "Bearer " + this.token));
	}

	@When("{string} reads the current principal with the token as the query parameter {string}")
	public void readMeWithQuery(String name, String parameter) throws Exception {
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me?" + parameter + "=" + this.token)));
	}

	@When("{string} reads the current principal with its token and the session cookie of the system admin")
	public void readMeWithCookie(String name) throws Exception {
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Authorization", "Bearer " + this.token)
			.header("Cookie", "__Host-OEA_SESSION=" + this.instance.sessionToken()));
	}

	@When("the current principal is read with a token that was never issued")
	public void readMeWithUnknown() throws Exception {
		this.token = AccessTokens.TOKEN_PREFIX + "never-issued-" + UUID.randomUUID();
		readMe(null);
	}

	@When("{string} lists the service accounts with its token")
	public void listAccounts(String name) throws Exception {
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/admin/service-accounts"))
			.header("Authorization", "Bearer " + this.token));
	}

	@When("{string} changes a password with its token")
	public void changePassword(String name) throws Exception {
		this.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me/password"))
			.header("Authorization", "Bearer " + this.token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}")));
	}

	// Outcomes

	@Then("the token response is 200 with a token starting with {string}, the type {string} and {int} seconds")
	public void issued(String prefix, String type, int seconds) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		var body = LocalLoginSteps.json(this.response);
		assertThat(body.path("access_token").asString()).startsWith(prefix);
		assertThat(body.path("token_type").asString()).isEqualTo(type);
		assertThat(body.path("expires_in").asInt()).isEqualTo(seconds);
		assertThat(body.has("refresh_token")).isFalse();
	}

	@Then("the token response must not be cached")
	public void noStore() {
		assertThat(this.response.headers().firstValue("Cache-Control")).hasValue("no-store");
		assertThat(this.response.headers().firstValue("Pragma")).hasValue("no-cache");
	}

	@Then("the last use of {string} is set")
	public void lastUse(String name) throws Exception {
		assertThat(this.instance.single("SELECT (last_used_at IS NOT NULL)::text FROM machine_credentials WHERE client_id = '"
				+ this.accounts.clientId(name) + "' AND status = 'active'")).isEqualTo("true");
	}

	@Then("the token request fails with {int} and the error {string}")
	public void failed(int status, String error) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(LocalLoginSteps.json(this.response).path("error").asString()).isEqualTo(error);
		if (status == 401) {
			assertThat(this.response.headers().firstValue("WWW-Authenticate")).hasValue("Basic realm=\"oea\"");
		}
	}

	@Then("the token request is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the token request is throttled with a Retry-After header")
	public void throttled() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(429);
		assertThat(this.response.headers().firstValue("Retry-After")).isPresent();
	}

	@Then("no token of {string} is stored")
	public void noToken(String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM access_tokens WHERE client_id = '"
				+ this.accounts.clientId(name) + "'")).isEqualTo("0");
	}

	@Then("all four token responses have the same status {int}, headers and body")
	public void sameRefusal(int status) {
		HttpResponse<String> first = this.refusals.getFirst();
		for (HttpResponse<String> refusal : this.refusals) {
			assertThat(refusal.statusCode()).isEqualTo(status);
			assertThat(refusal.body()).isEqualTo(first.body());
			for (String header : List.of("WWW-Authenticate", "Cache-Control", "Pragma", "Content-Type")) {
				assertThat(refusal.headers().firstValue(header)).as(header).isEqualTo(first.headers().firstValue(header));
			}
		}
	}

	@Then("the stored token of {string} is the SHA-256 hash of the token and expires 900 seconds after its issue")
	public void storedHash(String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM access_tokens WHERE client_id = '"
				+ this.accounts.clientId(name) + "' AND token_hash = decode('" + hash(this.token)
				+ "', 'hex') AND expires_at = issued_at + interval '900 seconds'")).isEqualTo("1");
	}

	@Then("no audit event contains the token or the secret")
	public void notAudited() throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE strpos(details::text, '"
				+ this.token + "') > 0")).isEqualTo("0");
	}

	@Then("the write with the token is answered with {int}")
	public void writeAnswered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the audit events of the write carry the actor type {string} and the client ID of {string}")
	public void writeActor(String type, String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE event_type LIKE 'entity.%' "
				+ "AND actor_type = '" + type + "' AND agent_id = '" + this.accounts.clientId(name) + "'")).isNotEqualTo("0");
	}

	@Then("no entity {string} exists")
	public void noEntity(String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM entities WHERE name = '" + name + "'")).isEqualTo("0");
	}

	@Then("the request with the token is answered with {int}")
	public void tokenRequestAnswered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the request with the token is answered with {int} and a Bearer challenge")
	public void bearerChallenge(int status) {
		tokenRequestAnswered(status);
		assertThat(this.response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
				(value) -> assertThat(value).startsWith("Bearer"));
	}

	@Then("the current principal is of the type {string}")
	public void principalType(String type) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(LocalLoginSteps.json(this.response).path("principal").path("principalType").asString()).isEqualTo(type);
	}

	@Then("only the token expired 23 hours and 59 minutes ago is kept")
	public void onlyRecentKept() throws Exception {
		assertThat(count(this.kept)).isEqualTo("1");
		assertThat(count(this.deleted)).isEqualTo("0");
	}

	private String count(String token) throws Exception {
		return this.instance.single("SELECT count(*)::text FROM access_tokens WHERE token_hash = decode('" + hash(token)
				+ "', 'hex')");
	}

	private void expire(String token, String ago) throws Exception {
		this.instance.superuser("UPDATE access_tokens SET issued_at = now() - interval '" + ago
				+ "' - interval '900 seconds', expires_at = now() - interval '" + ago + "' WHERE token_hash = decode('"
				+ hash(token) + "', 'hex')");
	}

	private HttpResponse<String> token(String clientId, String secret, String body) throws Exception {
		String basic = Base64.getEncoder()
			.encodeToString((encode(clientId) + ":" + encode(secret)).getBytes(StandardCharsets.UTF_8));
		return this.instance.send(HttpRequest.newBuilder(this.instance.uri(TOKEN))
			.header("Authorization", "Basic " + basic)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private void remember() {
		if (this.response.statusCode() == 200) {
			this.token = LocalLoginSteps.json(this.response).path("access_token").asString();
			this.instance.sensitive(this.token);
		}
	}

	private static String hash(String token) {
		return HexFormat.of().formatHex(AccessTokens.hash(token));
	}

	private static String encode(String value) {
		return URLEncoder.encode((value != null) ? value : "", StandardCharsets.UTF_8);
	}

}
