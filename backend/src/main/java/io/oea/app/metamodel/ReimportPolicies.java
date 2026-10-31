package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a package re-import keeps or removes organization mappings (design D8 of
 * us-032-metatype-editing): globally and per metatype, resolved along the base types.
 */
@Service
public class ReimportPolicies {

	private static final Set<String> GLOBAL = Set.of("keep", "remove");

	private static final Set<String> PER_METATYPE = Set.of("inherit", "keep", "remove");

	private final JdbcTemplate jdbc;

	private final MetatypeCatalog catalog;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	public ReimportPolicies(JdbcTemplate jdbc, MetatypeCatalog catalog, MetamodelEffects effects,
			MetamodelAudit audit) {
		this.jdbc = jdbc;
		this.catalog = catalog;
		this.effects = effects;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public String global() {
		return this.catalog.globalReimportPolicy();
	}

	@Transactional
	public void updateGlobal(OeaPrincipal principal, String policy) {
		if (!GLOBAL.contains(policy)) {
			throw MetamodelChangeException.invalid("organizationMappings", "Unknown setting.");
		}
		this.effects.lock();
		String before = this.catalog.globalReimportPolicy();
		this.jdbc.update("UPDATE metamodel_configurations SET reimport_organization_mappings = ? WHERE id = ?", policy,
				PackageImporter.INSTANCE_CONFIGURATION);
		this.audit.changed("metamodel.reimport-policy-changed", principal, "metamodel-configuration",
				PackageImporter.INSTANCE_CONFIGURATION, details(before, policy));
	}

	@Transactional
	public void updateMetatype(OeaPrincipal principal, UUID metatypeId, String policy) {
		if (!PER_METATYPE.contains(policy)) {
			throw MetamodelChangeException.invalid("organizationMappings", "Unknown setting.");
		}
		this.effects.lock();
		String before = this.jdbc
			.queryForList("SELECT reimport_organization_mappings FROM metatypes WHERE id = ?", String.class, metatypeId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + metatypeId));
		this.jdbc.update("UPDATE metatypes SET reimport_organization_mappings = ? WHERE id = ?", policy, metatypeId);
		this.audit.changed("metamodel.reimport-policy-changed", principal, "metatype", metatypeId,
				details(before, policy));
	}

	/** The setting that applies to each metatype. */
	Map<UUID, String> effective() {
		return this.catalog.effectiveReimportPolicies();
	}

	private static Map<String, Object> details(String before, String after) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("before", before);
		details.put("after", after);
		return details;
	}

}
