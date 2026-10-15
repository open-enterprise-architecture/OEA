package io.oea.core.domain;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Entities with application-assigned IDs: {@code save()} inserts new instances instead of
 * merging them, so that unique keys (e.g. the single settings row) are enforced by INSERT.
 */
@MappedSuperclass
public abstract class NewAwareEntity<ID> implements Persistable<ID> {

	@Transient
	private boolean newEntity = true;

	@Override
	public boolean isNew() {
		return this.newEntity;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		this.newEntity = false;
	}

}
