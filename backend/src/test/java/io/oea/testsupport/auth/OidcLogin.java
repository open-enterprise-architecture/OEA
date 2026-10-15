package io.oea.testsupport.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Headless authorization code flow with PKCE against the development realm (ADR-036 §5):
 * follows the real login page, submits the password and, for users with a second factor,
 * the TOTP form, then redeems the code at the token endpoint. No token is ever minted by
 * test code. Tokens are cached per issuer, client and user for the test run.
 */
public final class OidcLogin {

	/** Redirect URI registered for the portal client; the helper stops before calling it. */
	public static final String PORTAL_REDIRECT_URI = "http://localhost:5173/api/v1/auth/oidc/callback";

	private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*\\saction=\"([^\"]+)\"");

	private static final Pattern ERROR_MESSAGE = Pattern.compile("kc-feedback-text\">([^<]+)<|id=\"input-error[^\"]*\"[^>]*>\\s*([^<]+)<");

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Map<String, Tokens> CACHE = new ConcurrentHashMap<>();

	private OidcLogin() {
	}

	/** Tokens of a successful login. */
	public record Tokens(String accessToken, String idToken, String refreshToken) {

		/** Decoded claims of the access token (no signature check; the application verifies). */
		public JsonNode accessTokenClaims() {
			return claims(this.accessToken);
		}

		/** Decoded claims of the ID token (no signature check). */
		public JsonNode idTokenClaims() {
			return claims(this.idToken);
		}

		private static JsonNode claims(String token) {
			String payload = token.split("\\.")[1];
			return JsonMapper.shared().readTree(Base64.getUrlDecoder().decode(payload));
		}

	}

	/** Logs in a development user with the portal client. */
	public static Tokens login(String issuer, String username) {
		return login(issuer, "oea-portal", PORTAL_REDIRECT_URI, username);
	}

	public static Tokens login(String issuer, String clientId, String redirectUri, String username) {
		return CACHE.computeIfAbsent(issuer + "|" + clientId + "|" + username,
				(key) -> performLogin(issuer, clientId, redirectUri, username));
	}

	private static Tokens performLogin(String issuer, String clientId, String redirectUri, String username) {
		HttpClient http = HttpClient.newBuilder()
			.cookieHandler(new LoopbackCookieJar())
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		String verifier = randomToken(48);
		String state = randomToken(16);
		Map<String, String> authorize = new LinkedHashMap<>();
		authorize.put("client_id", clientId);
		authorize.put("response_type", "code");
		authorize.put("scope", "openid");
		authorize.put("redirect_uri", redirectUri);
		authorize.put("state", state);
		authorize.put("nonce", randomToken(16));
		authorize.put("code_challenge", challenge(verifier));
		authorize.put("code_challenge_method", "S256");

		HttpResponse<String> page = authenticate(http,
				URI.create(issuer + "/protocol/openid-connect/auth?" + form(authorize)), username);
		String location = page.headers().firstValue("Location").orElse(null);
		if (page.statusCode() != 302 || location == null || !location.startsWith(redirectUri)) {
			throw new IllegalStateException("Login of " + username + " failed (HTTP " + page.statusCode() + "): "
					+ errorMessage(page.body()));
		}
		Map<String, String> callback = query(URI.create(location).getRawQuery());
		if (!state.equals(callback.get("state"))) {
			throw new IllegalStateException("State mismatch in the authorization response");
		}

		Map<String, String> exchange = new LinkedHashMap<>();
		exchange.put("grant_type", "authorization_code");
		exchange.put("code", callback.get("code"));
		exchange.put("redirect_uri", redirectUri);
		exchange.put("client_id", clientId);
		exchange.put("code_verifier", verifier);
		String clientSecret = DevRealm.clientSecret(clientId);
		if (clientSecret != null) {
			exchange.put("client_secret", clientSecret);
		}
		HttpResponse<String> token = send(http,
				HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form(exchange))));
		if (token.statusCode() != 200) {
			throw new IllegalStateException("Token request for " + username + " failed (HTTP " + token.statusCode() + ")");
		}
		JsonNode body = JsonMapper.shared().readTree(token.body());
		return new Tokens(body.path("access_token").asString(), body.path("id_token").asString(),
				body.path("refresh_token").asString());
	}

	/** Result of a login through the application's own flow (design D2 of us-001-oidc-login). */
	public record ApplicationLogin(int status, String location, String sessionToken) {
	}

	/**
	 * Drives the application's OIDC login like a browser: the start endpoint, the login forms of
	 * the IdP, then the application's callback with the login cookie. The IdP redirects to the
	 * public URL; the helper sends that callback to the running application instead.
	 *
	 * @param applicationUrl base URL of the running application, e.g. {@code http://localhost:41234}
	 * @param publicUrl public URL the application is configured with (registered redirect URI)
	 * @param authorizationRequest lets a test change the authorization request (e.g. drop PKCE)
	 */
	public static ApplicationLogin loginThroughApplication(String applicationUrl, String publicUrl, UUID providerId,
			String username, UnaryOperator<URI> authorizationRequest) {
		HttpClient browser = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		HttpResponse<String> start = send(browser, HttpRequest
			.newBuilder(URI.create(applicationUrl + "/api/v1/auth/oidc/" + providerId + "/start?returnTo=/me"))
			.GET());
		String authorization = start.headers().firstValue("Location").orElseThrow();
		String loginCookie = cookie(start, "__Host-OEA_LOGIN");
		if (loginCookie == null) {
			return new ApplicationLogin(start.statusCode(), authorization, null);
		}
		HttpClient idp = HttpClient.newBuilder()
			.cookieHandler(new LoopbackCookieJar())
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		URI request = authorizationRequest.apply(URI.create(authorization));
		HttpResponse<String> first = send(idp, HttpRequest.newBuilder(request).GET());
		HttpResponse<String> page = (first.statusCode() == 200) ? authenticateOnPage(idp, first, username) : first;
		String callback = page.headers().firstValue("Location").orElseThrow(
				() -> new IllegalStateException("No redirect from the IdP: " + errorMessage(page.body())));
		if (!callback.startsWith(publicUrl)) {
			throw new IllegalStateException("IdP redirected elsewhere: " + callback);
		}
		HttpResponse<String> done = send(browser,
				HttpRequest.newBuilder(URI.create(applicationUrl + callback.substring(publicUrl.length())))
					.header("Cookie", "__Host-OEA_LOGIN=" + loginCookie)
					.GET());
		return new ApplicationLogin(done.statusCode(), done.headers().firstValue("Location").orElse(null),
				cookie(done, "__Host-OEA_SESSION"));
	}

	private static String cookie(HttpResponse<String> response, String name) {
		for (String header : response.headers().allValues("Set-Cookie")) {
			if (header.startsWith(name + "=")) {
				String value = header.substring(name.length() + 1, header.indexOf(';'));
				return value.isEmpty() ? null : value;
			}
		}
		return null;
	}

	/** Opens the authorization request and passes the password and, if any, the OTP form. */
	private static HttpResponse<String> authenticate(HttpClient http, URI authorizationRequest, String username) {
		return authenticateOnPage(http, send(http, HttpRequest.newBuilder(authorizationRequest).GET()), username);
	}

	private static HttpResponse<String> authenticateOnPage(HttpClient http, HttpResponse<String> loginPage,
			String username) {
		HttpResponse<String> page = submit(http, loginPage, Map.of("username", username, "password",
				DevRealm.password(username), "credentialId", ""));
		String totpSecret = DevRealm.totpSecret(username);
		if (page.statusCode() == 200 && totpSecret != null) {
			page = submit(http, page, Map.of("otp", Totp.code(totpSecret, Instant.now())));
		}
		return page;
	}

	private static HttpResponse<String> submit(HttpClient http, HttpResponse<String> page, Map<String, String> fields) {
		Matcher action = FORM_ACTION.matcher(page.body());
		if (page.statusCode() != 200 || !action.find()) {
			throw new IllegalStateException("Expected a login form (HTTP " + page.statusCode() + ")");
		}
		String target = action.group(1).replace("&amp;", "&");
		return send(http, HttpRequest.newBuilder(page.uri().resolve(target))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form(fields))));
	}

	private static HttpResponse<String> send(HttpClient http, HttpRequest.Builder request) {
		try {
			return http.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Request to the identity provider failed", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static String errorMessage(String body) {
		Matcher matcher = ERROR_MESSAGE.matcher(body);
		return matcher.find() ? ((matcher.group(1) != null) ? matcher.group(1) : matcher.group(2)).strip()
				: "no error message on the page";
	}

	private static String form(Map<String, String> fields) {
		return fields.entrySet()
			.stream()
			.map((e) -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
					+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
	}

	private static Map<String, String> query(String rawQuery) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String pair : rawQuery.split("&")) {
			int separator = pair.indexOf('=');
			values.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
		}
		return values;
	}

	private static String randomToken(int bytes) {
		byte[] value = new byte[bytes];
		RANDOM.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static String challenge(String verifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
