package io.oea.app.login;

import io.oea.app.security.IdpRoles;
import io.oea.app.security.IdpSessionRefresh;
import io.oea.app.security.PermissionResolver;
import io.oea.config.oidc.InvalidIdTokenException;
import io.oea.config.oidc.OidcClient;
import io.oea.config.oidc.OidcClientSettings;
import io.oea.config.oidc.OidcProviderUnavailableException;
import io.oea.config.oidc.OidcTokenRejectedException;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.core.domain.auth.LoginSession;
import io.oea.core.repository.AuthProviderRepository;
import io.oea.core.repository.PersonIdentityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Re-evaluation of the group claims at the latest 15 minutes after the previous one (design
 * D7, REQ-165 AC5, ADR-032 Q16/Q17): refresh grant with the stored refresh token, rotation,
 * validation of the new ID token and replacement of the derived roles. A rejected refresh
 * ends the session; an unreachable IdP withdraws the IdP-derived roles until a later attempt.
 */
@Service
public class IdpReevaluation implements IdpSessionRefresh {

	static final Duration RETRY_DELAY = Duration.ofMinutes(1);

	private final AuthProviderRepository providers;

	private final PersonIdentityRepository identities;

	private final OidcClient client;

	private final OidcLoginFlow login;

	private final IdpRoles idpRoles;

	private final PermissionResolver permissions;

	private final SecretCipher cipher;

	private final EntityManager entityManager;

	private final Clock clock;

	public IdpReevaluation(AuthProviderRepository providers, PersonIdentityRepository identities, OidcClient client,
			OidcLoginFlow login, IdpRoles idpRoles, PermissionResolver permissions, SecretCipher cipher,
			EntityManager entityManager, Clock clock) {
		this.providers = providers;
		this.identities = identities;
		this.client = client;
		this.login = login;
		this.idpRoles = idpRoles;
		this.permissions = permissions;
		this.cipher = cipher;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/** Runs in the transaction of the session authentication. */
	@Override
	public boolean refreshIfDue(LoginSession session) {
		// one refresh per session at a time; a parallel request sees the result
		this.entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
		Instant now = this.clock.instant();
		if (session.getRevokedAt() != null) {
			return false;
		}
		if (!session.isIdpEvaluationDue(now, IdpRoles.MAXIMUM_AGE)) {
			return true;
		}
		AuthProvider provider = this.providers.findById(session.getAuthProviderId()).orElse(null);
		if (provider == null || !provider.isActive()) {
			return false;
		}
		if (session.getIdpRefreshTokenEncrypted() == null) {
			unavailable(session, "refresh-unavailable", now);
			return true;
		}
		String refreshToken = this.cipher.decrypt(new SecretCipher.Encrypted(session.getIdpRefreshTokenEncrypted(),
				session.getIdpRefreshTokenKeyId()), LoginSession.refreshTokenEncryptionContext(session.getId()));
		OidcClientSettings settings = this.login.settings(provider);
		OidcClient.Tokens tokens;
		try {
			tokens = this.client.refresh(settings, refreshToken);
		}
		catch (OidcTokenRejectedException ex) {
			return false;
		}
		catch (OidcProviderUnavailableException ex) {
			unavailable(session, "idp-unreachable", now);
			return true;
		}
		this.login.storeRefreshToken(session, tokens);
		if (tokens.idToken() == null) {
			unavailable(session, "refresh-unavailable", now);
			return true;
		}
		Jwt idToken;
		try {
			idToken = this.client.validateIdToken(settings, tokens.idToken(), null);
		}
		catch (InvalidIdTokenException ex) {
			return false;
		}
		boolean samePerson = this.identities.findByIssuerAndSubject(provider.getIssuer(), idToken.getSubject())
			.filter((identity) -> identity.getPersonId().equals(session.getPersonId()))
			.isPresent();
		if (!samePerson) {
			return false;
		}
		this.idpRoles.evaluate(session, provider.getIssuer(), IdpRoles.GroupsClaim.of(idToken.getClaims()),
				AuthenticationMethods.secondFactor(idToken.getClaimAsStringList("amr")), "refresh");
		this.permissions.evictSession(session.getId());
		return true;
	}

	private void unavailable(LoginSession session, String reason, Instant now) {
		this.idpRoles.withdraw(session, reason);
		session.scheduleIdpRetry(now, IdpRoles.MAXIMUM_AGE, RETRY_DELAY);
		this.permissions.evictSession(session.getId());
	}

}
