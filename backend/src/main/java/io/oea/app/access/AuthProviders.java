package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.secrets.SecretCipher;
import io.oea.config.startup.LoopbackAddresses;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.core.repository.AuthProviderRepository;
import io.oea.core.repository.IdpGroupMappingRepository;
import io.oea.core.repository.PersonIdentityRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OIDC providers configured through the admin API (design D10): issuer rules G2/G3 of
 * ADR-036 §6 outside the profile {@code dev}, write-only encrypted client secret, fresh
 * authentication for every change (REQ-172) and audit event {@code auth-provider.changed}.
 */
@Service
public class AuthProviders {

	private static final Set<String> VENDORS = Set.of("entra-id", "authentik", "generic-oidc");

	private final AuthProviderRepository providers;

	private final PersonIdentityRepository identities;

	private final IdpGroupMappingRepository mappings;

	private final SecretCipher cipher;

	private final RecentAuthentication recentAuthentication;

	private final AuditWriter audit;

	private final Environment environment;

	private final Clock clock;

	public AuthProviders(AuthProviderRepository providers, PersonIdentityRepository identities,
			IdpGroupMappingRepository mappings, SecretCipher cipher, RecentAuthentication recentAuthentication,
			AuditWriter audit, Environment environment, Clock clock) {
		this.providers = providers;
		this.identities = identities;
		this.mappings = mappings;
		this.cipher = cipher;
		this.recentAuthentication = recentAuthentication;
		this.audit = audit;
		this.environment = environment;
		this.clock = clock;
	}

	/**
	 * Requested provider settings.
	 *
	 * @param clientSecret new client secret; {@code null} keeps the stored one
	 * @param claimMappings not supported in this change; must be empty
	 */
	public record Settings(String type, String vendor, String name, String issuerUrl, String clientId,
			String clientSecret, String scopes, boolean enabled, Map<String, String> claimMappings) {
	}

	/** A provider as shown to administrators; the client secret is never part of it. */
	public record Provider(UUID id, String name, String vendor, String issuer, String clientId, String scopes,
			boolean enabled, boolean clientSecretSet) {

		static Provider of(AuthProvider provider) {
			return new Provider(provider.getId(), provider.getName(), provider.getProviderType(), provider.getIssuer(),
					provider.getClientId(), provider.getScopes(), provider.isActive(), provider.hasClientSecret());
		}

	}

	@Transactional(readOnly = true)
	public List<Provider> list() {
		return this.providers.findAllByOrderByName().stream().map(Provider::of).toList();
	}

	@Transactional(readOnly = true)
	public Provider get(UUID id) {
		return Provider.of(find(id));
	}

	private AuthProvider find(UUID id) {
		return this.providers.findById(id).orElseThrow(() -> new NoSuchElementException("no provider " + id));
	}

	@Transactional
	public Provider create(OeaPrincipal principal, Settings settings) {
		this.recentAuthentication.require(principal);
		String issuer = validate(settings);
		if (this.providers.existsByIssuer(issuer)) {
			throw new InvalidAccessRequestException("issuerUrl", "A provider with this issuer is already configured.");
		}
		Instant now = this.clock.instant();
		AuthProvider provider = new AuthProvider(UUID.randomUUID(), now);
		apply(provider, settings, issuer, now);
		this.providers.save(provider);
		audit(principal, provider, "created", settings.clientSecret() != null);
		return Provider.of(provider);
	}

	@Transactional
	public Provider update(OeaPrincipal principal, UUID id, Settings settings) {
		this.recentAuthentication.require(principal);
		AuthProvider provider = find(id);
		String issuer = validate(settings);
		if (!issuer.equals(provider.getIssuer())) {
			if (this.providers.existsByIssuer(issuer)) {
				throw new InvalidAccessRequestException("issuerUrl",
						"A provider with this issuer is already configured.");
			}
			if (this.identities.existsByAuthProviderId(id) || this.mappings.existsLiveForProvider(id)) {
				// bindings and mappings are keyed by the issuer (REQ-166, REQ-167)
				throw new InvalidAccessRequestException("issuerUrl",
						"The issuer cannot change while identity bindings or group mappings refer to the provider.");
			}
		}
		apply(provider, settings, issuer, this.clock.instant());
		audit(principal, provider, "updated", settings.clientSecret() != null);
		return Provider.of(provider);
	}

	private void apply(AuthProvider provider, Settings settings, String issuer, Instant now) {
		String scopes = (settings.scopes() == null || settings.scopes().isBlank()) ? "openid profile email"
				: settings.scopes().strip();
		provider.configure((settings.vendor() != null) ? settings.vendor() : "generic-oidc", settings.name().strip(),
				issuer, settings.clientId().strip(), scopes, settings.enabled(), now);
		if (settings.clientSecret() != null) {
			SecretCipher.Encrypted encrypted = this.cipher.encrypt(settings.clientSecret(),
					AuthProvider.encryptionContext(provider.getId()));
			provider.storeClientSecret(encrypted.ciphertext(), encrypted.keyId());
		}
	}

	/** Returns the issuer if the settings are acceptable. */
	private String validate(Settings settings) {
		if (!"oidc".equals(settings.type())) {
			throw new InvalidAccessRequestException("type", "Only OIDC providers can be configured.");
		}
		if (settings.vendor() != null && !VENDORS.contains(settings.vendor())) {
			throw new InvalidAccessRequestException("vendor", "Unknown vendor.");
		}
		if (settings.name() == null || settings.name().isBlank()) {
			throw new InvalidAccessRequestException("name", "The name is required.");
		}
		if (settings.clientId() == null || settings.clientId().isBlank()) {
			throw new InvalidAccessRequestException("clientId", "The client ID is required.");
		}
		if (settings.claimMappings() != null && !settings.claimMappings().isEmpty()) {
			throw new InvalidAccessRequestException("claimMappings", "Claim mappings are not supported yet.");
		}
		if (settings.issuerUrl() == null || settings.issuerUrl().isBlank()) {
			throw new InvalidAccessRequestException("issuerUrl", "The issuer is required.");
		}
		String issuer = settings.issuerUrl().strip();
		URI uri;
		try {
			uri = URI.create(issuer);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidAccessRequestException("issuerUrl", "The issuer is not a valid URL.");
		}
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (uri.getHost() == null || !Set.of("http", "https").contains(scheme)) {
			throw new InvalidAccessRequestException("issuerUrl", "The issuer is not a valid URL.");
		}
		if (!this.environment.acceptsProfiles(Profiles.of("dev"))) {
			// G2/G3 of ADR-036 §6, the same rules as the startup guards
			if (!"https".equals(scheme)) {
				throw new InvalidAccessRequestException("issuerUrl", "The issuer " + issuer + " must use https.");
			}
			if (LoopbackAddresses.isLoopbackUrl(issuer)) {
				throw new InvalidAccessRequestException("issuerUrl",
						"The issuer " + issuer + " is a loopback address, which is allowed only in development.");
			}
		}
		return issuer;
	}

	private void audit(OeaPrincipal principal, AuthProvider provider, String action, boolean secretChanged) {
		Map<String, Object> details = new HashMap<>();
		details.put("action", action);
		details.put("issuer", provider.getIssuer());
		details.put("enabled", provider.isActive());
		details.put("clientSecretChanged", secretChanged);
		this.audit.append(new AuditEvent("auth-provider.changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"auth-provider", provider.getId().toString(), details));
	}

}
