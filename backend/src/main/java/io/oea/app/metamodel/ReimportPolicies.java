package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.metamodel.MetamodelConfigurationStore;
import io.oea.core.metamodel.MetatypeStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
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

	private final MetamodelConfigurationStore configurationStore;

	private final MetatypeStore metatypes;

	private final MetatypeCatalog catalog;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelVersions versions;

	public ReimportPolicies(MetamodelConfigurationStore configurationStore, MetatypeStore metatypes,
			MetatypeCatalog catalog, MetamodelEffects effects,
			MetamodelAudit audit, MetamodelVersions versions) {
		this.configurationStore = configurationStore;
		this.metatypes = metatypes;
		this.catalog = catalog;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
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
		this.configurationStore.setReimportPolicy(PackageImporter.INSTANCE_CONFIGURATION, policy);
		this.audit.changed("metamodel.reimport-policy-changed", principal, "metamodel-configuration",
				PackageImporter.INSTANCE_CONFIGURATION, details(before, policy));
	}

	@Transactional
	public void updateMetatype(OeaPrincipal principal, UUID metatypeId, String policy, Long expectedVersion) {
		this.effects.lock();
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		if (!PER_METATYPE.contains(policy)) {
			throw MetamodelChangeException.invalid("organizationMappings", "Unknown setting.");
		}
		String before = this.metatypes.reimportPolicy(metatypeId)
			.orElseThrow(() -> new NoSuchElementException("no metatype " + metatypeId));
		this.metatypes.setReimportPolicy(metatypeId, policy);
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
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
