package io.oea.api.admin;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.AdminApi;
import io.oea.api.generated.model.AuthProvider;
import io.oea.api.generated.model.AuthProviderWrite;
import io.oea.api.generated.model.ImportMetamodelPackageRequest;
import io.oea.api.generated.model.ImportResult;
import io.oea.api.generated.model.StarterPackage;
import io.oea.app.access.AuthProviders;
import io.oea.app.metamodel.PackageConflictException;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.metamodel.StarterPackages;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration: OIDC providers (design D10 of us-013-system-admin-login; deleting and testing
 * a provider are not implemented, a provider is deactivated with {@code enabled: false}) and
 * metamodel package imports (us-033-import-metamodel).
 */
@RestController
public class AdminController implements AdminApi {

	private final AuthProviders providers;

	private final PackageImporter packageImporter;

	private final StarterPackages starterPackages;

	public AdminController(AuthProviders providers, PackageImporter packageImporter,
			StarterPackages starterPackages) {
		this.providers = providers;
		this.packageImporter = packageImporter;
		this.starterPackages = starterPackages;
	}

	@Override
	public ResponseEntity<List<StarterPackage>> listStarterPackages() {
		return ResponseEntity.ok(this.starterPackages.list()
			.stream()
			.map((summary) -> {
				StarterPackages.Shipped shipped = summary.shipped();
				return new StarterPackage(shipped.packageId(), shipped.name(), shipped.version(), shipped.publisher(),
						shipped.description(), shipped.metatypeNames().size(), shipped.propertyDefinitionCount(),
						shipped.metatypeNames(), summary.imported());
			})
			.toList());
	}

	@Override
	public ResponseEntity<ImportResult> importStarterPackage(String packageId) {
		ImportMetamodelPackageRequest request = MetamodelPackages.read(this.starterPackages.get(packageId).content());
		try {
			return ResponseEntity.ok(MetamodelPackages
				.result(this.packageImporter.importPackage(principal(), MetamodelPackages.definition(request))));
		}
		catch (PackageConflictException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(MetamodelPackages.result(ex));
		}
	}

	@Override
	@ContractViolationResponse(shape = ContractViolationResponse.Shape.PACKAGE, status = 422, title = "Invalid package",
			detail = "The package is not valid JSON or does not match the package format.")
	public ResponseEntity<ImportResult> importMetamodelPackage(ImportMetamodelPackageRequest request) {
		try {
			return ResponseEntity.ok(MetamodelPackages
				.result(this.packageImporter.importPackage(principal(), MetamodelPackages.definition(request))));
		}
		catch (PackageConflictException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(MetamodelPackages.result(ex));
		}
	}

	@Override
	public ResponseEntity<List<AuthProvider>> listAuthProviders() {
		return ResponseEntity.ok(this.providers.list().stream().map(AdminController::provider).toList());
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
