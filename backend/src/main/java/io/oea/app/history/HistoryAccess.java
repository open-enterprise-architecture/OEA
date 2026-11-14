package io.oea.app.history;

import io.oea.app.metamodel.HistoryVisibility;
import io.oea.app.metamodel.PropertyAccess;
import java.util.UUID;

/**
 * The visibility rule of the change history (design D2 of us-098-entity-history, D2 of
 * us-099-version-diff), shared by the timeline and the versions: a property the reader may read
 * under the current visibility (REQ-160) is readable; otherwise it is hidden when every role of
 * the reader hides it for the entity's metatype ({@link HistoryVisibility}), and restricted when
 * not. System fields are always readable.
 */
final class HistoryAccess {

	/** How a property appears in the history. */
	enum Visibility {

		READABLE, RESTRICTED, HIDDEN

	}

	private final PropertyAccess.Snapshot access;

	private final boolean hide;

	HistoryAccess(PropertyAccess.Snapshot access, boolean hide) {
		this.access = access;
		this.hide = hide;
	}

	PropertyAccess.Snapshot access() {
		return this.access;
	}

	/** How the property of the entity appears for the reader. */
	Visibility of(UUID propertyDefId, int entityId) {
		if (this.access.readableFor(propertyDefId, entityId)) {
			return Visibility.READABLE;
		}
		return this.hide ? Visibility.HIDDEN : Visibility.RESTRICTED;
	}

}
