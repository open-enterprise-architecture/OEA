package io.oea.api.access;

import io.oea.api.generated.AccessControlApi;
import io.oea.api.generated.model.AuthorizationRole;
import io.oea.api.generated.model.Permission;
import io.oea.app.access.AccessCatalog;
import io.oea.config.security.ContractAuthorizationRules;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Permission catalog and authorization roles (US-143); further operations follow (UC-23). */
@RestController
public class AccessControlController implements AccessControlApi {

	private final AccessCatalog catalog;

	private final ContractAuthorizationRules rules;

	public AccessControlController(AccessCatalog catalog, ContractAuthorizationRules rules) {
		this.catalog = catalog;
		this.rules = rules;
	}

	@Override
	public ResponseEntity<List<Permission>> listPermissions() {
		return ResponseEntity.ok(this.catalog.permissions()
			.stream()
			.map((p) -> new Permission(p.id(), p.category(), p.adminClass(),
					this.rules.permissionDescriptions().getOrDefault(p.id(), "")))
			.toList());
	}

	@Override
	public ResponseEntity<List<AuthorizationRole>> listAuthorizationRoles(Boolean assignableToServiceAccounts) {
		return ResponseEntity.ok(this.catalog.roles(assignableToServiceAccounts)
			.stream()
			.map((r) -> new AuthorizationRole(r.id(), r.name(), r.builtin(), r.privileged(),
					r.assignableToServiceAccounts(), r.overridesPropertyVisibility(), new LinkedHashSet<>(r.permissions()))
				.description(r.description()))
			.toList());
	}

}
