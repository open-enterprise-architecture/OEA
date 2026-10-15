package io.oea.api.admin;

import io.oea.api.generated.AdminApi;
import io.oea.api.generated.model.AuthProvider;
import io.oea.api.generated.model.AuthProviderWrite;
import io.oea.app.access.AuthProviders;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * OIDC providers (design D10). Deleting and testing a provider are not implemented in this
 * change; a provider is deactivated with {@code enabled: false}.
 */
@RestController
public class AuthProviderController implements AdminApi {

	private final AuthProviders providers;

	public AuthProviderController(AuthProviders providers) {
		this.providers = providers;
	}

	@Override
	public ResponseEntity<List<AuthProvider>> listAuthProviders() {
		return ResponseEntity.ok(this.providers.list().stream().map(AuthProviderController::provider).toList());
	}

	@Override
	public ResponseEntity<AuthProvider> getAuthProvider(UUID id) {
		return ResponseEntity.ok(provider(this.providers.get(id)));
	}

	@Override
	public ResponseEntity<AuthProvider> createAuthProvider(AuthProviderWrite request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(provider(this.providers.create(principal(), settings(request))));
	}

	@Override
	public ResponseEntity<AuthProvider> updateAuthProvider(UUID id, AuthProviderWrite request) {
		return ResponseEntity.ok(provider(this.providers.update(principal(), id, settings(request))));
	}

	private static AuthProviders.Settings settings(AuthProviderWrite request) {
		return new AuthProviders.Settings(request.getType().getValue(),
				(request.getVendor() != null) ? request.getVendor().getValue() : null, request.getName(),
				request.getIssuerUrl(), request.getClientId(), request.getClientSecret(), request.getScopes(),
				!Boolean.FALSE.equals(request.getEnabled()), request.getClaimMappings());
	}

	private static AuthProvider provider(AuthProviders.Provider provider) {
		return new AuthProvider(provider.id(), provider.name(), AuthProvider.TypeEnum.OIDC, provider.enabled())
			.vendor(AuthProvider.VendorEnum.fromValue(provider.vendor()))
			.issuerUrl(provider.issuer())
			.clientId(provider.clientId())
			.scopes(provider.scopes())
			.clientSecretSet(provider.clientSecretSet());
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
