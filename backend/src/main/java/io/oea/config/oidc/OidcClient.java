package io.oea.config.oidc;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * OIDC client for the stored providers (design D1). Discovery documents and key sets are
 * cached per issuer for an hour; every HTTP call to a provider has a 5-second timeout.
 */
@Component
@EnableConfigurationProperties(OidcProperties.class)
public class OidcClient {

	static final Duration TIMEOUT = Duration.ofSeconds(5);

	static final Duration METADATA_LIFETIME = Duration.ofHours(1);

	/** Asymmetric algorithms only: no configuration can enable {@code none} or {@code HS*} (G1). */
	static final List<SignatureAlgorithm> ALGORITHMS = List.of(SignatureAlgorithm.RS256, SignatureAlgorithm.RS384,
			SignatureAlgorithm.RS512, SignatureAlgorithm.PS256, SignatureAlgorithm.PS384, SignatureAlgorithm.PS512,
			SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.ES512);

	private final RestClient http;

	/** Token endpoint client: form requests, OAuth2 token responses and error bodies. */
	private final RestClient tokenHttp;

	private final Clock clock;

	private final Map<String, Metadata> metadata = new ConcurrentHashMap<>();

	private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

	private final Map<String, String> discoveryLocations;

	public OidcClient(Clock clock) {
		this(clock, new OidcProperties(List.of()));
	}

	@Autowired
	public OidcClient(Clock clock, OidcProperties properties) {
		this.discoveryLocations = properties.discoveryLocations()
			.stream()
			.collect(java.util.stream.Collectors.toUnmodifiableMap(OidcProperties.DiscoveryLocation::issuer,
					OidcProperties.DiscoveryLocation::location));
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
		requests.setReadTimeout(TIMEOUT);
		this.http = RestClient.builder().requestFactory(requests).build();
		this.tokenHttp = RestClient.builder()
			.requestFactory(requests)
			.configureMessageConverters((converters) -> converters.addCustomConverter(new FormHttpMessageConverter())
				.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
			.build();
		this.clock = clock;
	}

	/** Endpoints of a provider from its discovery document (RFC 8414, OIDC Discovery). */
	record Metadata(String issuer, String authorizationEndpoint, String tokenEndpoint, String jwksUri,
			Instant fetchedAt) {
	}

	/** Tokens of a successful token request; the access token is not used. */
	public record Tokens(String idToken, String refreshToken, Instant refreshTokenExpiresAt) {

		@Override
		public String toString() {
			return "Tokens[refreshTokenExpiresAt=" + this.refreshTokenExpiresAt + "]";
		}

	}

	/** URI of the authorization request with PKCE {@code S256}, {@code state} and {@code nonce}. */
	public URI authorizationUri(OidcClientSettings settings, String redirectUri, String state, String nonce,
			String codeChallenge) {
		return UriComponentsBuilder.fromUriString(metadata(settings.issuer()).authorizationEndpoint())
			.queryParam("response_type", "code")
			.queryParam("client_id", settings.clientId())
			.queryParam("redirect_uri", redirectUri)
			.queryParam("scope", String.join(" ", settings.scopes()))
			.queryParam("state", state)
			.queryParam("nonce", nonce)
			.queryParam(PkceParameterNames.CODE_CHALLENGE, codeChallenge)
			.queryParam(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256")
			.encode()
			.build()
			.toUri();
	}

	/** Redeems the authorization code with the PKCE verifier. */
	public Tokens redeem(OidcClientSettings settings, String redirectUri, String code, String state,
			String codeVerifier) {
		ClientRegistration registration = registration(settings, redirectUri);
		OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri(registration.getProviderDetails().getAuthorizationUri())
			.clientId(settings.clientId())
			.redirectUri(redirectUri)
			.state(state)
			.attributes((attributes) -> attributes.put(PkceParameterNames.CODE_VERIFIER, codeVerifier))
			.build();
		OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
			.redirectUri(redirectUri)
			.state(state)
			.build();
		RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
		client.setRestClient(this.tokenHttp);
		return tokens(() -> client.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registration,
				new OAuth2AuthorizationExchange(request, response))));
	}

	/** Refresh grant; the provider may rotate the refresh token. */
	public Tokens refresh(OidcClientSettings settings, String refreshToken) {
		ClientRegistration registration = registration(settings, "{baseUrl}");
		Instant now = this.clock.instant();
		// the grant request needs an access token object; only the refresh token is sent
		OAuth2AccessToken unused = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "unused",
				now.minusSeconds(2), now.minusSeconds(1));
		RestClientRefreshTokenTokenResponseClient client = new RestClientRefreshTokenTokenResponseClient();
		client.setRestClient(this.tokenHttp);
		return tokens(() -> client.getTokenResponse(new OAuth2RefreshTokenGrantRequest(registration, unused,
				new OAuth2RefreshToken(refreshToken, null), registration.getScopes())));
	}

	/**
	 * Validates an ID token (REQ-166 AC2): signature against the provider's keys, issuer,
	 * audience, {@code azp}, expiry, issue time and, at login, the nonce.
	 *
	 * @param expectedNonce nonce of the running login, or {@code null} for a refreshed token
	 */
	public Jwt validateIdToken(OidcClientSettings settings, String idToken, String expectedNonce) {
		ClientRegistration registration = registration(settings, "{baseUrl}");
		OidcIdTokenValidator standard = new OidcIdTokenValidator(registration);
		standard.setClock(this.clock);
		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
		validators.add(standard);
		validators.add(claim("azp", (jwt) -> jwt.getClaimAsString("azp") == null
				|| settings.clientId().equals(jwt.getClaimAsString("azp"))));
		if (expectedNonce != null) {
			validators.add(claim(OidcParameterNames.NONCE,
					(jwt) -> expectedNonce.equals(jwt.getClaimAsString(OidcParameterNames.NONCE))));
		}
		Jwt jwt;
		try {
			jwt = decoder(registration).decode(idToken);
		}
		catch (JwtException ex) {
			throw new InvalidIdTokenException("ID token signature or format rejected", ex);
		}
		OAuth2TokenValidatorResult result = new DelegatingOAuth2TokenValidator<>(validators).validate(jwt);
		if (result.hasErrors()) {
			throw new InvalidIdTokenException("ID token rejected: " + result.getErrors()
				.stream()
				.map(OAuth2Error::getErrorCode)
				.distinct()
				.toList(), null);
		}
		return jwt;
	}

	/** Drops cached discovery data, e.g. after a provider was changed. */
	public void forget(String issuer) {
		this.metadata.remove(issuer);
		this.decoders.keySet().removeIf((key) -> key.startsWith(issuer + " "));
	}

	private ClientRegistration registration(OidcClientSettings settings, String redirectUri) {
		Metadata provider = metadata(settings.issuer());
		return ClientRegistration.withRegistrationId(settings.providerId().toString())
			.clientId(settings.clientId())
			.clientSecret(settings.clientSecret())
			.clientAuthenticationMethod((settings.clientSecret() != null) ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
					: ClientAuthenticationMethod.NONE)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(redirectUri)
			.scope(settings.scopes())
			.issuerUri(provider.issuer())
			.authorizationUri(provider.authorizationEndpoint())
			.tokenUri(provider.tokenEndpoint())
			.jwkSetUri(provider.jwksUri())
			.build();
	}

	private Metadata metadata(String issuer) {
		Instant now = this.clock.instant();
		Metadata cached = this.metadata.get(issuer);
		if (cached != null && cached.fetchedAt().plus(METADATA_LIFETIME).isAfter(now)) {
			return cached;
		}
		// split-horizon networks (D13): the document may be fetched elsewhere, its issuer is still checked
		String base = this.discoveryLocations.getOrDefault(issuer, issuer);
		String location = base.endsWith("/") ? base + ".well-known/openid-configuration"
				: base + "/.well-known/openid-configuration";
		JsonNode document;
		try {
			document = this.http.get().uri(location).retrieve().body(JsonNode.class);
		}
		catch (RestClientException ex) {
			throw new OidcProviderUnavailableException("discovery of " + issuer + " failed", ex);
		}
		if (document == null || !issuer.equals(document.path("issuer").asString(null))) {
			// OIDC Discovery §4.3: the document must name exactly the configured issuer
			throw new OidcProviderUnavailableException("discovery of " + issuer + " names another issuer", null);
		}
		Metadata fetched = new Metadata(issuer, required(document, "authorization_endpoint", issuer),
				required(document, "token_endpoint", issuer), required(document, "jwks_uri", issuer), now);
		this.metadata.put(issuer, fetched);
		return fetched;
	}

	private JwtDecoder decoder(ClientRegistration registration) {
		String issuer = registration.getProviderDetails().getIssuerUri();
		String jwks = registration.getProviderDetails().getJwkSetUri();
		return this.decoders.computeIfAbsent(issuer + " " + jwks, (key) -> {
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks)
				.jwsAlgorithms((algorithms) -> algorithms.addAll(ALGORITHMS))
				.build();
			// claims are validated separately with the injectable clock
			decoder.setJwtValidator((jwt) -> OAuth2TokenValidatorResult.success());
			return decoder;
		});
	}

	private Tokens tokens(java.util.function.Supplier<OAuth2AccessTokenResponse> request) {
		OAuth2AccessTokenResponse response;
		try {
			response = request.get();
		}
		catch (OAuth2AuthorizationException ex) {
			String code = ex.getError().getErrorCode();
			if (ex.getCause() instanceof RestClientException && !"invalid_grant".equals(code)
					&& !"invalid_client".equals(code)) {
				throw new OidcProviderUnavailableException("token request failed", ex);
			}
			throw new OidcTokenRejectedException(code, ex);
		}
		catch (RestClientException ex) {
			throw new OidcProviderUnavailableException("token request failed", ex);
		}
		Object idToken = response.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);
		Object refreshExpiresIn = response.getAdditionalParameters().get("refresh_expires_in");
		Instant refreshExpiresAt = null;
		if (refreshExpiresIn instanceof Number seconds && seconds.longValue() > 0) {
			refreshExpiresAt = this.clock.instant().plusSeconds(seconds.longValue());
		}
		return new Tokens(Objects.toString(idToken, null),
				(response.getRefreshToken() != null) ? response.getRefreshToken().getTokenValue() : null,
				refreshExpiresAt);
	}

	private static String required(JsonNode document, String field, String issuer) {
		String value = document.path(field).asString(null);
		if (value == null || value.isBlank()) {
			throw new OidcProviderUnavailableException("discovery of " + issuer + " lacks " + field, null);
		}
		return value;
	}

	private static OAuth2TokenValidator<Jwt> claim(String name, java.util.function.Predicate<Jwt> check) {
		return (jwt) -> check.test(jwt) ? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_" + name));
	}

}
