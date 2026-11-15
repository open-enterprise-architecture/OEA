package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.core.service.PasswordHasher;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

/**
 * Steps for features/identity/service-accounts.feature and the setup phase scenarios of
 * access-administration.feature (us-142-service-accounts); the instance and the system admin
 * session come from {@link LocalLoginSteps}, persons from {@link RoleAssignmentSteps} of the same
 * scenario. Every client secret is registered for the log check of REQ-175.
 */
public class ServiceAccountSteps {

	private static final String BASE = "/api/v1/admin/service-accounts";

	private final LocalLoginSteps instance;

	private final RoleAssignmentSteps persons;

	private final Map<String, String> clientIds = new HashMap<>();

	/** The current secret of each service account of the scenario, by name. */
	private final Map<String, String> secrets = new HashMap<>();

	private String createdSecret;

	private String createdClientId;

	private final List<String> reads = new ArrayList<>();

	public ServiceAccountSteps(LocalLoginSteps instance, RoleAssignmentSteps persons) {
		this.instance = instance;
		this.persons = persons;
	}

	// Creating

	@When("the system admin creates the service account {string} with the roles {string}")
	public void createWithRoles(String name, String roles) throws Exception {
		create(null, body(name, roles(roles), null));
	}

	@When("the system admin creates the service account {string} without roles")
	public void createWithoutRoles(String name) throws Exception {
		create(null, body(name, null, null));
	}

	@Given("the system admin has created the service account {string}")
	public void created(String name) throws Exception {
		createWithoutRoles(name);
		assertCreated();
	}

	@Given("the system admin has created the service account {string} with the roles {string}")
	public void createdWithRoles(String name, String roles) throws Exception {
		createWithRoles(name, roles);
		assertCreated();
	}

	@Given("the system admin has created the service accounts {string} and {string}")
	public void createdTwo(String first, String second) throws Exception {
		created(first);
		created(second);
	}

	@When("{string} creates the service account {string}")
	public void personCreates(String person, String name) throws Exception {
		create(person, body(name, null, null));
	}

	@When("{string} creates the service account {string} with the roles {string}")
	public void personCreatesWithRoles(String person, String name, String roles) throws Exception {
		create(person, body(name, roles(roles), null));
	}

	@When("the system admin creates a service account with a name of {int} characters")
	public void createWithNameLength(int length) throws Exception {
		String name = (length == 0) ? "" : "n".repeat(length);
		create(null, body(name, null, null));
	}

	@When("the system admin creates a service account with a secret validity of {int} days")
	public void createWithValidity(int days) throws Exception {
		create(null, body("validity-" + days, null, days));
	}

	@Given("{int} custom roles assignable to service accounts exist")
	public void assignableRoles(int count) throws Exception {
		for (int n = 1; n <= count; n++) {
			this.instance.superuser("INSERT INTO authorization_roles (id, name, is_builtin, assignable_to_service_accounts) "
					+ "VALUES ('reader-" + n + "', 'Reader " + n + "', false, true)");
		}
	}

	@When("the system admin creates a service account with {int} of them")
	public void createWithRoleCount(int count) throws Exception {
		List<String> roles = IntStream.rangeClosed(1, count).mapToObj((n) -> "reader-" + n).toList();
		create(null, body("many-roles", roles, null));
	}

	// Reading

	@When("the system admin reads the service account {string} and the list of service accounts")
	public void read(String name) throws Exception {
		this.reads.add(this.instance.get(BASE + "/" + this.clientIds.get(name)).body());
		this.reads.add(this.instance.get(BASE).body());
	}

	@When("the system admin lists the service accounts")
	public void list() throws Exception {
		this.instance.response = this.instance.get(BASE);
	}

	@When("the system admin lists the active service accounts")
	public void listActive() throws Exception {
		this.instance.response = this.instance.get(BASE + "?active=true");
	}

	// Changing

	@When("the system admin rotates the secret of {string}")
	public void rotate(String name) throws Exception {
		this.instance.response = this.instance.post(BASE + "/" + this.clientIds.get(name) + "/credentials/rotate", "{}",
				true);
		remember(this.instance.response);
		if (this.instance.response.statusCode() == 200) {
			this.previousSecrets.put(name, this.secrets.get(name));
			this.secrets.put(name, LocalLoginSteps.json(this.instance.response).path("clientSecret").asString());
		}
	}

	/** The secrets of the service accounts before their last rotation, by name. */
	private final Map<String, String> previousSecrets = new HashMap<>();

	String clientId(String name) {
		return this.clientIds.get(name);
	}

	String secret(String name) {
		return this.secrets.get(name);
	}

	String previousSecret(String name) {
		return this.previousSecrets.get(name);
	}

	@When("the system admin rotates the secret of an unknown client ID")
	public void rotateUnknown() throws Exception {
		this.instance.response = this.instance.post(BASE + "/" + UUID.randomUUID() + "/credentials/rotate", "{}", true);
	}

	@When("the system admin changes the roles of {string} to {string}")
	public void changeRoles(String name, String roles) throws Exception {
		this.instance.response = this.instance.put(BASE + "/" + this.clientIds.get(name), body(name, roles(roles), null));
	}

	@When("the system admin deactivates {string}")
	public void deactivate(String name) throws Exception {
		this.instance.response = this.instance.delete(BASE + "/" + this.clientIds.get(name));
	}

	@Given("the system admin has deactivated {string}")
	public void deactivated(String name) throws Exception {
		deactivate(name);
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(204);
	}

	@Given("the system admin authenticated {int} minutes and {int} second(s) ago")
	public void authenticatedAgo(int minutes, int seconds) throws Exception {
		this.instance.superuser("UPDATE sessions SET authenticated_at = now() - interval '" + minutes + " minutes "
				+ seconds + " seconds' WHERE system_admin_account_id IS NOT NULL AND revoked_at IS NULL");
	}

	@Given("the secret of {string} expires in exactly 14 days and the secret of {string} in 14 days and one minute")
	public void expiries(String first, String second) throws Exception {
		expiry(first, "14 days");
		expiry(second, "14 days 1 minute");
	}

	// Outcomes

	@Then("the service account is created with a client ID and a client secret")
	public void createdWithSecret() {
		assertCreated();
		assertThat(UUID.fromString(this.createdClientId)).isNotNull();
		assertThat(this.createdSecret).isNotBlank();
	}

	@Then("a person of type {string} named {string} exists")
	public void personExists(String type, String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM persons WHERE person_type = '" + type
				+ "' AND name = '" + name + "'")).isEqualTo("1");
	}

	@Then("the service account {string} holds the roles {string}")
	public void holds(String name, String roles) throws Exception {
		JsonNode account = LocalLoginSteps.json(this.instance.get(BASE + "/" + this.clientIds.get(name)));
		List<String> held = new ArrayList<>();
		account.path("roles").forEach((role) -> held.add(role.asString()));
		assertThat(String.join(", ", held)).isEqualTo(roles);
	}

	@Then("no service account {string} exists")
	public void noAccount(String name) throws Exception {
		assertThat(this.instance.single(
				"SELECT count(*)::text FROM persons WHERE person_type = 'service-account' AND name = '" + name + "'"))
			.isEqualTo("0");
	}

	@Then("the service account request is answered with {int}")
	public void answered(int status) {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(status);
	}

	@Then("only the creation response contains the secret, which starts with {string}")
	public void secretOnce(String prefix) {
		assertThat(this.createdSecret).startsWith(prefix);
		assertThat(this.reads).hasSize(2).allSatisfy((body) -> {
			assertThat(body).doesNotContain(this.createdSecret).doesNotContain("clientSecret");
		});
	}

	@Then("the stored secret of {string} is an argon2id hash that differs from the secret")
	public void storedHash(String name) throws Exception {
		String hash = this.instance.single("SELECT client_secret_hash FROM machine_credentials WHERE client_id = '"
				+ this.clientIds.get(name) + "' AND status = 'active'");
		assertThat(hash).startsWith("$argon2id$").isNotEqualTo(this.createdSecret);
		assertThat(new PasswordHasher().matches(this.createdSecret, hash)).isTrue();
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE strpos(details::text, '"
				+ this.createdSecret + "') > 0")).isEqualTo("0");
	}

	@Then("the secret of {string} expires 90 days after its creation")
	public void defaultExpiry(String name) throws Exception {
		JsonNode account = LocalLoginSteps.json(this.instance.get(BASE + "/" + this.clientIds.get(name)));
		Instant created = Instant.parse(account.path("createdAt").asString());
		Instant expires = Instant.parse(account.path("secretExpiresAt").asString());
		assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofDays(90));
	}

	@Then("{string} is marked as expiring soon and {string} is not")
	public void expiringSoon(String soon, String later) {
		Map<String, Boolean> marks = new HashMap<>();
		LocalLoginSteps.json(this.instance.response)
			.forEach((account) -> marks.put(account.path("name").asString(), account.path("secretExpiresSoon").asBoolean()));
		assertThat(marks).containsEntry(soon, true).containsEntry(later, false);
	}

	@Then("the response contains a new secret and the same client ID")
	public void rotated() {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
		JsonNode body = LocalLoginSteps.json(this.instance.response);
		assertThat(body.path("id").asString()).isEqualTo(this.createdClientId);
		assertThat(body.path("clientSecret").asString()).startsWith("oea_cs_").isNotEqualTo(this.createdSecret);
	}

	@Then("the secrets of {string} are {string}")
	public void secrets(String name, String statuses) throws Exception {
		assertThat(this.instance.single("SELECT string_agg(status, ', ' ORDER BY created_at, status = 'active') "
				+ "FROM machine_credentials WHERE client_id = '" + this.clientIds.get(name) + "'")).isEqualTo(statuses);
	}

	@Then("the problem names {string}")
	public void problemNames(String text) {
		assertThat(LocalLoginSteps.json(this.instance.response).path("detail").asString()).contains(text);
	}

	@Then("the revoked assignment of {string} to {string} keeps its history")
	public void revokedKept(String role, String name) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM role_assignments WHERE person_id = "
				+ personId(name) + " AND role_id = '" + role
				+ "' AND status = 'revoked' AND revoked_at IS NOT NULL AND granted_at IS NOT NULL")).isEqualTo("1");
	}

	@Then("the permissions of {string} contain {string} and not {string}")
	public void permissions(String name, String granted, String missing) throws Exception {
		OeaPrincipal principal = new OeaPrincipal(OeaPrincipal.Type.PERSON, null, personId(name), name, null, false,
				Instant.now());
		Set<String> permissions = this.instance.bean(PermissionResolver.class).permissions(principal);
		assertThat(permissions).contains(granted).doesNotContain(missing);
	}

	@Then("the service account {string} is {string} without an active secret and without roles")
	public void inactive(String name, String status) throws Exception {
		JsonNode account = LocalLoginSteps.json(this.instance.get(BASE + "/" + this.clientIds.get(name)));
		assertThat(account.path("status").asString()).isEqualTo(status);
		assertThat(account.path("roles")).isEmpty();
		assertThat(account.path("secretExpiresAt").isNull()).isTrue();
		assertThat(this.instance.single("SELECT count(*)::text FROM machine_credentials WHERE client_id = '"
				+ this.clientIds.get(name) + "' AND status = 'active'")).isEqualTo("0");
	}

	@Then("the list contains {string} and not {string}")
	public void listContains(String listed, String missing) {
		List<String> names = new ArrayList<>();
		LocalLoginSteps.json(this.instance.response).forEach((account) -> names.add(account.path("name").asString()));
		assertThat(names).contains(listed).doesNotContain(missing);
	}

	@Then("the setup phase has not ended and no service account exists")
	public void noAccountInSetupPhase() throws Exception {
		assertThat(this.instance.single("SELECT (setup_phase_ended_at IS NULL)::text FROM system_settings"))
			.isEqualTo("true");
		assertThat(this.instance.single("SELECT count(*)::text FROM persons WHERE person_type = 'service-account'"))
			.isEqualTo("0");
	}

	private void create(String person, String body) throws Exception {
		if (person == null) {
			this.instance.response = this.instance.post(BASE, body, true);
		}
		else {
			String token = this.persons.sessionToken(person);
			this.instance.response = this.instance.send(HttpRequest.newBuilder(this.instance.uri(BASE))
				.header("Content-Type", "application/json")
				.header("Cookie", "__Host-OEA_SESSION=" + token)
				.header("X-XSRF-TOKEN", Sessions.csrfToken(token))
				.POST(HttpRequest.BodyPublishers.ofString(body)));
		}
		if (this.instance.response.statusCode() == 201) {
			JsonNode created = LocalLoginSteps.json(this.instance.response);
			this.createdClientId = created.path("id").asString();
			this.clientIds.put(created.path("name").asString(), this.createdClientId);
			this.secrets.put(created.path("name").asString(), created.path("clientSecret").asString());
		}
		remember(this.instance.response);
	}

	private void remember(HttpResponse<String> response) {
		if (response.statusCode() == 201 || response.statusCode() == 200) {
			String secret = LocalLoginSteps.json(response).path("clientSecret").asString(null);
			if (secret != null) {
				this.instance.sensitive(secret);
				if (response.statusCode() == 201) {
					this.createdSecret = secret;
				}
			}
		}
	}

	private void assertCreated() {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(201);
	}

	private void expiry(String name, String interval) throws Exception {
		this.instance.superuser("UPDATE machine_credentials SET expires_at = now() + interval '" + interval
				+ "' WHERE client_id = '" + this.clientIds.get(name) + "' AND status = 'active'");
	}

	private long personId(String name) throws Exception {
		return Long.parseLong(this.instance.single("SELECT person_id::text FROM machine_credentials WHERE client_id = '"
				+ this.clientIds.get(name) + "' LIMIT 1"));
	}

	private static List<String> roles(String roles) {
		return Arrays.stream(roles.split(",")).map(String::strip).toList();
	}

	private static String body(String name, List<String> roles, Integer validity) {
		StringBuilder body = new StringBuilder("{\"name\":\"").append(name).append('"');
		if (roles != null) {
			body.append(",\"roles\":[")
				.append(roles.stream().map((role) -> '"' + role + '"').collect(Collectors.joining(",")))
				.append(']');
		}
		if (validity != null) {
			body.append(",\"secretValidityDays\":").append(validity);
		}
		return body.append('}').toString();
	}

}
