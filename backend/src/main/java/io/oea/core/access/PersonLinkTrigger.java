package io.oea.core.access;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Marks the inputs of the account link through the mapping as changed (design D5 of
 * us-075-lane-assignments): a value of a property, the deletion or restore of an entity, an
 * account, an identity binding, the mapping or the person metatype. The recomputation runs once
 * per transaction, before it commits, so that a rejection (SoD-1) rolls the whole request back.
 * Stores call it with the cheapest signal they have; the recomputation itself decides whether the
 * signal matters for the mapping that is set.
 */
@Component
public class PersonLinkTrigger {

	/** A trigger that does nothing, for stores used without the application context. */
	public static final PersonLinkTrigger NONE = new PersonLinkTrigger(null);

	/** The recomputation of the links through the mapping, implemented by the application layer. */
	public interface Recompute {

		/**
		 * Recomputes before the commit.
		 *
		 * @param properties the properties whose values changed in the transaction
		 * @param structural whether something else than a value changed (an entity deleted or
		 *        restored, an account, a binding, the mapping)
		 */
		void recompute(Set<UUID> properties, boolean structural);

	}

	private static final Object KEY = new Object();

	private final ObjectProvider<Recompute> recompute;

	public PersonLinkTrigger(ObjectProvider<Recompute> recompute) {
		this.recompute = recompute;
	}

	/** A value of the property changed. */
	public void valueChanged(UUID propertyDefId) {
		State state = state();
		if (state != null) {
			state.properties.add(propertyDefId);
		}
	}

	/** Something else than a value changed. */
	public void changed() {
		State state = state();
		if (state != null) {
			state.structural = true;
		}
	}

	private State state() {
		if (this.recompute == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
			return null;
		}
		State state = (State) TransactionSynchronizationManager.getResource(KEY);
		if (state == null) {
			state = new State();
			TransactionSynchronizationManager.bindResource(KEY, state);
			TransactionSynchronizationManager.registerSynchronization(new Synchronization(state, this.recompute));
		}
		return state;
	}

	private static final class State {

		private final Set<UUID> properties = new HashSet<>();

		private boolean structural;

	}

	private static final class Synchronization implements TransactionSynchronization {

		private final State state;

		private final ObjectProvider<Recompute> recompute;

		Synchronization(State state, ObjectProvider<Recompute> recompute) {
			this.state = state;
			this.recompute = recompute;
		}

		@Override
		public void beforeCommit(boolean readOnly) {
			if (readOnly) {
				return;
			}
			Recompute target = this.recompute.getIfAvailable();
			if (target != null) {
				target.recompute(this.state.properties, this.state.structural);
			}
		}

		@Override
		public void afterCompletion(int status) {
			if (TransactionSynchronizationManager.hasResource(KEY)) {
				TransactionSynchronizationManager.unbindResource(KEY);
			}
		}

	}

}
