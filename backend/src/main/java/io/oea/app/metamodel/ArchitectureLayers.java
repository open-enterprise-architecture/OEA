package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.metamodel.ArchitectureLayerStore;
import io.oea.core.metamodel.ArchitectureLayerStore.LayerRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, renaming, reordering and deleting the architecture layers of the instance metamodel
 * (design D3 of s17-architecture-layers; US-057 AC4, REQ-053 BR-04, ADR-017): a name of 1 to 100
 * characters unique regardless of case, at most 20 layers, a dense order. Writes run under the
 * metamodel lock, are rejected in {@code import-only} and check {@code If-Match}; imported layers
 * are read-only.
 */
@Service
public class ArchitectureLayers {

	/** Maximum number of layers of the instance. */
	public static final int MAXIMUM_LAYERS = 20;

	static final int NAME_MAX_LENGTH = 100;

	private static final String NAME = "name";

	private static final String TARGET = "architecture-layer";

	private static final String ELEMENT = "architecture layer";

	private final ArchitectureLayerStore store;

	private final MetamodelEffects effects;

	private final MetamodelConfigurations configurations;

	private final MetamodelVersions versions;

	private final MetamodelAudit audit;

	ArchitectureLayers(ArchitectureLayerStore store, MetamodelEffects effects, MetamodelConfigurations configurations,
			MetamodelVersions versions, MetamodelAudit audit) {
		this.store = store;
		this.effects = effects;
		this.configurations = configurations;
		this.versions = versions;
		this.audit = audit;
	}

	/** The package a layer comes from. */
	public record SourcePackage(String packageId, String version) {
	}

	/** A layer with the number of metatypes of all configurations that have it. */
	public record Layer(UUID id, String name, int sortOrder, String scope, SourcePackage sourcePackage,
			long metatypeCount, int version) {

		static Layer of(LayerRow row) {
			return new Layer(row.id(), row.name(), row.sortOrder(), row.scope(),
					(row.sourcePackageId() != null) ? new SourcePackage(row.sourcePackageId(), row.sourcePackageVersion())
							: null,
					row.metatypeCount(), row.version());
		}

	}

	/** The layers in their order. */
	@Transactional(readOnly = true)
	public List<Layer> list() {
		return this.store.list().stream().map(Layer::of).toList();
	}

	/**
	 * One layer.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 */
	@Transactional(readOnly = true)
	public Layer get(UUID id) {
		return Layer.of(row(id));
	}

	/**
	 * Creates an organization layer, at the end or at the position, and audits
	 * {@code metamodel.layer-created}.
	 *
	 * @param position the position among the layers, or {@code null} for the end
	 * @throws MetamodelChangeException with {@code LOCKED} in {@code import-only} (403), {@code NAME_TAKEN}
	 *     (409) or {@code INVALID} for the name or the 21st layer (422)
	 */
	@Transactional
	public Layer create(OeaPrincipal principal, String name, Integer position) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		String stripped = checkedName(name, null);
		if (this.store.count() >= MAXIMUM_LAYERS) {
			throw MetamodelChangeException.invalid("architectureLayers",
					"An instance has at most " + MAXIMUM_LAYERS + " architecture layers.");
		}
		UUID id = UUID.randomUUID();
		if (!this.store.insert(id, stripped, MetamodelEffects.author(principal), this.effects.now())) {
			throw MetamodelChangeException.nameTaken(NAME);
		}
		if (position != null) {
			this.store.move(id, position);
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put(NAME, stripped);
		details.put("sortOrder", row(id).sortOrder());
		this.audit.changed("metamodel.layer-created", principal, TARGET, id, details);
		return get(id);
	}

	/**
	 * Renames the layer and, with a position, moves it; audits {@code metamodel.layer-updated}.
	 *
	 * @param position the position among the layers, or {@code null} to keep it
	 * @throws NoSuchElementException for an unknown ID (404)
	 * @throws MetamodelChangeException for an imported layer ({@code READ_ONLY}), without an expected
	 *     version (428), a stale version (412), a taken name (409) or an invalid name (422)
	 */
	@Transactional
	public Layer update(OeaPrincipal principal, UUID id, String name, Integer position, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		LayerRow current = writable(id, expectedVersion);
		String stripped = checkedName(name, id);
		List<String> changed = new ArrayList<>();
		if (!current.name().equals(stripped)) {
			changed.add(NAME);
			this.store.rename(id, stripped, MetamodelEffects.author(principal), this.effects.now());
		}
		if (position != null) {
			this.store.move(id, position);
			if (row(id).sortOrder() != current.sortOrder()) {
				changed.add("sortOrder");
			}
		}
		this.versions.increment(MetamodelVersions.Element.ARCHITECTURE_LAYER, id);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put(NAME, stripped);
		details.put("changedFields", changed);
		this.audit.changed("metamodel.layer-updated", principal, TARGET, id, details);
		return get(id);
	}

	/**
	 * Deletes the layer with its organization labels and audits {@code metamodel.layer-deleted}.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 * @throws MetamodelChangeException for an imported layer, without an expected version (428), a
	 *     stale version (412) or a layer that a metatype still has ({@code IN_USE}, 409)
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		LayerRow current = writable(id, expectedVersion);
		if (current.metatypeCount() > 0) {
			throw MetamodelChangeException.inUse(
					"The architecture layer is assigned to " + current.metatypeCount() + " metatypes.",
					current.metatypeCount());
		}
		this.store.delete(id);
		this.effects.removeOrphanLabels();
		this.audit.changed("metamodel.layer-deleted", principal, TARGET, id, Map.of(NAME, current.name()));
	}

	/** The layer, checked to be an organization layer at the expected version. */
	private LayerRow writable(UUID id, Long expectedVersion) {
		LayerRow current = row(id);
		// the origin is checked whatever the version, so that an imported layer never changes through the API
		if (!"organization".equals(current.scope())) {
			throw MetamodelChangeException.readOnly(ELEMENT);
		}
		if (expectedVersion == null) {
			throw MetamodelChangeException.preconditionRequired(ELEMENT);
		}
		this.versions.check(MetamodelVersions.Element.ARCHITECTURE_LAYER, id, expectedVersion);
		return current;
	}

	/** Checks the name and that no other layer has it; returns it stripped. */
	private String checkedName(String name, UUID self) {
		String stripped = (name != null) ? name.strip() : "";
		if (stripped.isEmpty() || stripped.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid(NAME, "The name must have 1 to " + NAME_MAX_LENGTH + " characters.");
		}
		if (this.store.nameTaken(stripped, self)) {
			throw MetamodelChangeException.nameTaken(NAME);
		}
		return stripped;
	}

	private LayerRow row(UUID id) {
		return this.store.find(id).orElseThrow(() -> new NoSuchElementException("no architecture layer " + id));
	}

}
