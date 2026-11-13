package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether the baseline of a solution marks entities of a metatype that another active solution
 * changes (design D3 of us-044-conflict-warning; US-044). A setting like the re-import setting: for
 * every scope, also while the metamodel is locked, kept by a re-import, and not inherited.
 */
@Service
public class ParallelChangeSettings {

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelVersions versions;

	public ParallelChangeSettings(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelVersions versions) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
	}

	/**
	 * Sets the value for a metatype, raises its version and audits
	 * {@code metamodel.metatype-parallel-changes-changed}.
	 *
	 * @throws NoSuchElementException for an unknown metatype (404)
	 */
	@Transactional
	public void update(OeaPrincipal principal, UUID metatypeId, boolean show, Long expectedVersion) {
		this.effects.lock();
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		Boolean before = this.jdbc
			.queryForList("SELECT show_parallel_changes FROM metatypes WHERE id = ?", Boolean.class, metatypeId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + metatypeId));
		this.jdbc.update("UPDATE metatypes SET show_parallel_changes = ? WHERE id = ?", show, metatypeId);
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("before", before);
		details.put("after", show);
		this.audit.changed("metamodel.metatype-parallel-changes-changed", principal, "metatype", metatypeId, details);
	}

}
