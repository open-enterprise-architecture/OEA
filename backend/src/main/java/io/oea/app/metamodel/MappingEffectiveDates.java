package io.oea.app.metamodel;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies mapping entries whose effective date is reached (design D3 of
 * us-137-mapping-effective-date; REQ-148, REQ-149): the marks of the property on the metatype and
 * its subtypes follow the necessity in effect, each entry once, audited as
 * {@code metamodel.mapping-effective}. Which necessity applies is decided by the date alone, so a
 * delayed run only delays the marks.
 */
@Component
public class MappingEffectiveDates {

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	public MappingEffectiveDates(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
	}

	/** @return the number of entries applied in this run */
	@Scheduled(fixedDelayString = "${oea.metamodel.effective-date-check-interval:PT5M}",
			initialDelayString = "${oea.metamodel.effective-date-check-interval:PT5M}")
	@Transactional
	public int applyDueEntries() {
		record Due(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, Date validFrom) {
		}
		Boolean pending = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM metatype_property_mappings
				               WHERE effective_applied_at IS NULL AND valid_from <= ?)""", Boolean.class,
				Date.valueOf(this.effects.today()));
		if (!Boolean.TRUE.equals(pending)) {
			return 0;
		}
		// several instances serialize on the lock; the entries are read after it, so each is applied once
		this.effects.lock();
		List<Due> due = this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from FROM metatype_property_mappings
				WHERE effective_applied_at IS NULL AND valid_from <= ? ORDER BY valid_from, id""",
				(rs, n) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getString(4), rs.getDate(5)),
				Date.valueOf(this.effects.today()));
		for (Due entry : due) {
			MetamodelEffects.Synced synced = this.effects.syncMarks(entry.propertyDefId(), entry.metatypeId());
			this.jdbc.update("UPDATE metatype_property_mappings SET effective_applied_at = ? WHERE id = ?",
					Timestamp.from(this.effects.now()), entry.id());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("mappingId", entry.id().toString());
			details.put("propertyDefId", entry.propertyDefId().toString());
			details.put("necessity", entry.necessity());
			details.put("validFrom", entry.validFrom().toString());
			details.put("entitiesMarked", synced.marked());
			details.put("marksCleared", synced.cleared());
			this.audit.bySystem("metamodel.mapping-effective", "metatype", entry.metatypeId(), details);
		}
		return due.size();
	}

}
