package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.VisibilityStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How changes to fields a reader may not read appear in the history of an entity, per
 * authorization role and metatype (US-098, REQ-098, owner decision 2026-11-14; design D3 of
 * us-098-entity-history): {@code show-restricted} (the default, stored as the absence of a row)
 * names the field as restricted, {@code hide} leaves it out. With several roles, the more open
 * setting applies. Only security administration ({@code visibility:configure}) changes it, and
 * every changed entry is audited.
 */
@Service
public class HistoryVisibility {

	public static final String SHOW_RESTRICTED = "show-restricted";

	public static final String HIDE = "hide";

	private static final Set<String> MODES = Set.of(SHOW_RESTRICTED, HIDE);

	private final VisibilityStore store;

	private final MetatypeStore metatypeStore;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	public HistoryVisibility(VisibilityStore store, MetatypeStore metatypeStore, PermissionResolver permissions, AuditWriter audit) {
		this.store = store;
		this.metatypeStore = metatypeStore;
		this.permissions = permissions;
		this.audit = audit;
	}

	/** One entry: the mode of a role for a metatype. */
	public record Entry(String roleId, UUID metatypeId, String mode) {
	}

	/** The configured entries ({@code hide} only) of roles that are not deleted, by role and metatype name. */
	@Transactional(readOnly = true)
	public List<Entry> list() {
		return this.store.history().stream().map((row) -> new Entry(row.roleId(), row.metatypeId(), row.mode())).toList();
	}

	/**
	 * Whether a field the reader may not read is left out of the history of an entity of the
	 * metatype: only when the reader has roles and every one is set to {@code hide} for exactly
	 * this metatype (no inheritance along base types). The entries of a deleted role count as
	 * absent, as in {@link #list()}.
	 */
	public boolean hidden(Collection<String> roles, UUID metatypeId) {
		Set<String> distinct = new HashSet<>(roles);
		if (distinct.isEmpty() || metatypeId == null) {
			return false;
		}
		return this.store.hidingRoles(metatypeId, distinct) == distinct.size();
	}

	/**
	 * Sets the listed entries in one transaction; {@code show-restricted} removes an entry. Every
	 * invalid entry is named in the 422 and nothing changes then: an unknown role or metatype, an
	 * unknown mode, or a pair listed twice. Each entry whose mode changes writes
	 * {@code visibility.history-changed}; an unchanged entry writes nothing.
	 *
	 * @return the configuration after the change
	 * @throws InvalidAccessRequestException naming e.g. {@code entries[1].roleId}
	 */
	@Transactional
	public List<Entry> update(OeaPrincipal principal, List<Entry> changes) {
		Set<String> roles = new HashSet<>(this.store.lockActiveRoleIds());
		Map<UUID, String> metatypes = this.metatypeStore.names();
		List<String> invalid = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < changes.size(); i++) {
			Entry change = changes.get(i);
			String prefix = "entries[" + i + "].";
			boolean knownRole = change.roleId() != null && roles.contains(change.roleId());
			boolean knownMetatype = change.metatypeId() != null && metatypes.containsKey(change.metatypeId());
			if (!knownRole) {
				invalid.add(prefix + "roleId");
			}
			if (!knownMetatype) {
				invalid.add(prefix + "metatypeId");
			}
			if (change.mode() == null || !MODES.contains(change.mode())) {
				invalid.add(prefix + "mode");
			}
			if (knownRole && knownMetatype && !seen.add(change.roleId() + " " + change.metatypeId())) {
				invalid.add(prefix + "roleId");
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidAccessRequestException(invalid,
					"The history visibility is invalid (" + String.join(", ", invalid) + ").");
		}
		Set<String> held = new HashSet<>(this.permissions.roles(principal));
		for (Entry change : changes) {
			String old = mode(change.roleId(), change.metatypeId());
			if (old.equals(change.mode())) {
				continue;
			}
			if (HIDE.equals(change.mode())) {
				this.store.hide(change.roleId(), change.metatypeId());
			}
			else {
				this.store.show(change.roleId(), change.metatypeId());
			}
			audit(principal, change, old, metatypes.get(change.metatypeId()), held);
		}
		return list();
	}

	private String mode(String roleId, UUID metatypeId) {
		return this.store.hides(roleId, metatypeId) ? HIDE : SHOW_RESTRICTED;
	}

	private void audit(OeaPrincipal principal, Entry change, String old, String metatypeName, Set<String> held) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("roleId", change.roleId());
		details.put("metatypeId", change.metatypeId().toString());
		details.put("metatype", metatypeName);
		details.put("oldMode", old);
		details.put("newMode", change.mode());
		details.put("selfBenefiting", SHOW_RESTRICTED.equals(change.mode()) && held.contains(change.roleId()));
		this.audit.append(new AuditEvent("visibility.history-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"history-visibility", change.roleId() + ":" + change.metatypeId(), details));
	}

}
