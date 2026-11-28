package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.ConnectionConstraintStore;
import io.oea.core.metamodel.LineageModelStore;
import io.oea.core.metamodel.LineageModelStore.Row;
import io.oea.core.metamodel.MetatypeStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lineage model of the instance (design D3 of us-065-lineage-model-and-query): which metatypes
 * play data object, data flow, carries-data, data input and data output. A metamodel administrator
 * sets the parts with {@code If-Match}; a package import fills the parts that are unset. The
 * permissions are checked from the contract; the metamodel lock mode applies to the write.
 */
@Service
public class LineageModels {

	/** The parts, with their API names. */
	public enum Part {

		DATA_OBJECT("dataObject", false), DATA_FLOW("dataFlow", true), CARRIES_DATA("carriesData", true),
		DATA_INPUT("dataInput", true), DATA_OUTPUT("dataOutput", true);

		private final String field;

		private final boolean connection;

		Part(String field, boolean connection) {
			this.field = field;
			this.connection = connection;
		}

		/** The name in the API. */
		public String field() {
			return this.field;
		}

		/** Whether the part is a connection metatype. */
		public boolean connection() {
			return this.connection;
		}

		/** The part named in the API, or empty. */
		public static Optional<Part> ofField(String field) {
			for (Part part : values()) {
				if (part.field.equals(field)) {
					return Optional.of(part);
				}
			}
			return Optional.empty();
		}

		/** The metatype of this part in the given parts, or {@code null}. */
		public UUID of(Parts parts) {
			return switch (this) {
				case DATA_OBJECT -> parts.dataObject();
				case DATA_FLOW -> parts.dataFlow();
				case CARRIES_DATA -> parts.carriesData();
				case DATA_INPUT -> parts.dataInput();
				case DATA_OUTPUT -> parts.dataOutput();
			};
		}

	}

	/** A constraint a connection part has to meet: its allowed endpoints must reach another part. */
	private record Reach(Part connection, String direction, Part endpoint) {
	}

	private static final List<Reach> REACHES = List.of(new Reach(Part.CARRIES_DATA, ConnectionConstraintStore.SOURCE, Part.DATA_FLOW),
			new Reach(Part.CARRIES_DATA, ConnectionConstraintStore.TARGET, Part.DATA_OBJECT),
			new Reach(Part.DATA_INPUT, ConnectionConstraintStore.SOURCE, Part.DATA_OBJECT),
			new Reach(Part.DATA_OUTPUT, ConnectionConstraintStore.TARGET, Part.DATA_OBJECT));

	private final LineageModelStore store;

	private final MetatypeStore metatypes;

	private final ConnectionConstraintStore constraints;

	private final MetamodelEffects effects;

	private final MetamodelConfigurations configurations;

	private final AuditWriter audit;

	private final Clock clock;

	LineageModels(LineageModelStore store, MetatypeStore metatypes, ConnectionConstraintStore constraints,
			MetamodelEffects effects, MetamodelConfigurations configurations, AuditWriter audit, Clock clock) {
		this.store = store;
		this.metatypes = metatypes;
		this.constraints = constraints;
		this.effects = effects;
		this.configurations = configurations;
		this.audit = audit;
		this.clock = clock;
	}

	/** The metatype IDs of the parts, each {@code null} while unset. */
	public record Parts(UUID dataObject, UUID dataFlow, UUID carriesData, UUID dataInput, UUID dataOutput) {

		static Parts of(LineageModelStore.Parts parts) {
			return new Parts(parts.dataObject(), parts.dataFlow(), parts.carriesData(), parts.dataInput(),
					parts.dataOutput());
		}

		LineageModelStore.Parts toStore() {
			return new LineageModelStore.Parts(this.dataObject, this.dataFlow, this.carriesData, this.dataInput,
					this.dataOutput);
		}

	}

	/** The metatype names of the parts, each {@code null} while unset. */
	public record Names(String dataObject, String dataFlow, String carriesData, String dataInput,
			String dataOutput) {

		static Names of(LineageModelStore.Names names) {
			return new Names(names.dataObject(), names.dataFlow(), names.carriesData(), names.dataInput(),
					names.dataOutput());
		}

	}

	/** The parts with the names of their metatypes and the version. */
	public record View(Parts parts, Names names, int version) {

		static View of(Row row) {
			return new View(Parts.of(row.parts()), Names.of(row.names()), row.version());
		}

	}

	/** The lineage model. */
	@Transactional(readOnly = true)
	public View get() {
		return View.of(this.store.get());
	}

	/**
	 * Sets the five parts with the expected version.
	 *
	 * @throws MetamodelChangeException in {@code import-only} (403), without the expected version
	 *     (428), with a stale one (412), for a part of the wrong kind or a connection part whose
	 *     endpoint lists exclude the part they have to reach (422, naming the part)
	 * @throws PermissionDeniedException for a principal without a person (403)
	 */
	@Transactional
	public View update(OeaPrincipal principal, Parts requested, Long expectedVersion) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		Row current = this.store.lock();
		if (expectedVersion == null) {
			throw MetamodelChangeException.preconditionRequired("lineage model");
		}
		if (expectedVersion != current.version()) {
			throw MetamodelChangeException.staleVersion("lineage model", current.version());
		}
		check(requested);
		Map<String, Object> changed = changes(Parts.of(current.parts()), requested);
		if (changed.isEmpty()) {
			return View.of(current);
		}
		Row updated = this.store.update(requested.toStore(), person, this.clock.instant());
		audit(principal, changed, updated.version(), null);
		return View.of(updated);
	}

	/**
	 * Fills the parts of the package that are unset and leaves the others; audits the filled parts
	 * with {@code cause: package-import}. The package validator checked the kind of the metatypes.
	 *
	 * @return the filled parts in the order of {@link Part}
	 */
	@Transactional
	public List<Part> fillUnset(OeaPrincipal principal, Parts fromPackage, String packageId) {
		Row current = this.store.lock();
		Parts now = Parts.of(current.parts());
		List<Part> filled = new ArrayList<>();
		UUID[] merged = new UUID[Part.values().length];
		for (Part part : Part.values()) {
			UUID existing = part.of(now);
			UUID offered = part.of(fromPackage);
			merged[part.ordinal()] = (existing != null) ? existing : offered;
			if (existing == null && offered != null) {
				filled.add(part);
			}
		}
		if (filled.isEmpty()) {
			return filled;
		}
		Parts result = new Parts(merged[0], merged[1], merged[2], merged[3], merged[4]);
		Row updated = this.store.updateBySystem(result.toStore(), this.clock.instant());
		audit(principal, changes(now, result), updated.version(), packageId);
		return filled;
	}

	private void check(Parts requested) {
		for (Part part : Part.values()) {
			UUID id = part.of(requested);
			if (id == null) {
				continue;
			}
			Optional<Boolean> connection = this.metatypes.isConnection(id);
			if (connection.isEmpty() || connection.get() != part.connection()) {
				throw MetamodelChangeException.invalid(part.field(), "The part " + part.field() + " must be an existing "
						+ (part.connection() ? "connection" : "element") + " metatype.");
			}
		}
		Map<UUID, UUID> bases = this.metatypes.bases();
		for (Reach reach : REACHES) {
			if (reach.connection().of(requested) == null || reach.endpoint().of(requested) == null) {
				continue;
			}
			List<UUID> allowed = this.constraints.of(reach.connection().of(requested)).get(reach.direction());
			if (!allowed.isEmpty()
					&& chain(reach.endpoint().of(requested), bases).stream().noneMatch(allowed::contains)) {
				throw MetamodelChangeException.invalid(reach.connection().field(),
						"The connection metatype of " + reach.connection().field() + " does not allow the "
								+ reach.endpoint().field() + " metatype as " + reach.direction() + ".");
			}
		}
	}

	private static List<UUID> chain(UUID metatype, Map<UUID, UUID> bases) {
		List<UUID> chain = new ArrayList<>();
		UUID next = metatype;
		while (next != null && !chain.contains(next)) {
			chain.add(next);
			next = bases.get(next);
		}
		return chain;
	}

	private static Map<String, Object> changes(Parts before, Parts after) {
		Map<String, Object> changed = new LinkedHashMap<>();
		for (Part part : Part.values()) {
			UUID old = part.of(before);
			UUID now = part.of(after);
			if (!Objects.equals(old, now)) {
				Map<String, Object> detail = new LinkedHashMap<>();
				detail.put("old", (old != null) ? old.toString() : "");
				detail.put("new", (now != null) ? now.toString() : "");
				changed.put(part.field(), detail);
			}
		}
		return changed;
	}

	private void audit(OeaPrincipal principal, Map<String, Object> changed, int version, String packageId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("parts", changed);
		details.put("version", version);
		if (packageId != null) {
			details.put("cause", "package-import");
			details.put("packageId", packageId);
		}
		this.audit.append(new AuditEvent("metamodel.lineage-model-updated", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"lineage-model", "1", details));
	}

}
