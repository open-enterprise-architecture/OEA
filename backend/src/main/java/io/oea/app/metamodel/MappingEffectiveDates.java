package io.oea.app.metamodel;

import io.oea.core.metamodel.PropertyMappingStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	private final PropertyMappingStore store;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	public MappingEffectiveDates(PropertyMappingStore store, MetamodelEffects effects, MetamodelAudit audit) {
		this.store = store;
		this.effects = effects;
		this.audit = audit;
	}

	/** @return the number of entries applied in this run */
	@Scheduled(fixedDelayString = "${oea.metamodel.effective-date-check-interval:PT5M}",
			initialDelayString = "${oea.metamodel.effective-date-check-interval:PT5M}")
	@Transactional
	public int applyDueEntries() {
		if (!this.store.dueExists(this.effects.today())) {
			return 0;
		}
		// several instances serialize on the lock; the entries are read after it, so each is applied once
		this.effects.lock();
		List<PropertyMappingStore.DueRow> due = this.store.due(this.effects.today());
		for (PropertyMappingStore.DueRow entry : due) {
			MetamodelEffects.Synced synced = this.effects.syncMarks(entry.propertyDefId(), entry.metatypeId());
			this.store.markApplied(entry.id(), this.effects.now());
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
