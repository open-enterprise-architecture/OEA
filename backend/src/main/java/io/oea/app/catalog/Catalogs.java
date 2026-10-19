package io.oea.app.catalog;

import io.oea.app.metamodel.PackageImporter;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.catalog.CatalogStore.StoredCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and reading catalogs (US-046, REQ-043; design D3–D6 of us-046-create-catalog). A
 * catalog with scope {@code instance} needs {@code catalog:manage}; a personal catalog is visible
 * only to its creator and answered as not existing for everybody else.
 */
@Service
public class Catalogs {

	/** Permission for shared catalogs, the contract condition of {@code createCatalog}. */
	public static final String MANAGE_PERMISSION = "catalog:manage";

	static final int NAME_MAX_LENGTH = 255;

	static final int DESCRIPTION_MAX_LENGTH = 1000;

	private final CatalogStore store;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	public Catalogs(CatalogStore store, PermissionResolver permissions, AuditWriter audit, Clock clock) {
		this.store = store;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Visibility of a catalog. */
	public enum Scope {

		INSTANCE, PERSONAL;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** Join mode of a catalog. */
	public enum JoinMode {

		AGGREGATE, ROWS;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** A catalog with the current name of its primary metatype and its creator. */
	public record Catalog(UUID id, String name, String description, UUID primaryMetatypeId, String primaryMetatypeName,
			String defaultJoinMode, String scope, long createdById, String createdByName, Instant createdAt,
			Instant lastModifiedAt) {

		static Catalog of(StoredCatalog stored) {
			return new Catalog(stored.id(), stored.name(), stored.description(), stored.primaryMetatypeId(),
					stored.primaryMetatypeName(), stored.defaultJoinMode(), stored.scope(), stored.createdById(),
					stored.createdByName(), stored.createdAt(), stored.lastModifiedAt());
		}

	}

	/** Input of a new catalog; a {@code null} join mode means {@code aggregate}. */
	public record NewCatalog(String name, String description, UUID primaryMetatypeId, Scope scope,
			JoinMode defaultJoinMode) {
	}

	/**
	 * Creates the catalog and audits it. Order: scope permission, fields, primary metatype, name
	 * (by the unique index, so concurrent creations cannot both succeed).
	 */
	@Transactional
	public Catalog create(OeaPrincipal principal, NewCatalog input) {
		long personId = person(principal);
		if (input.scope() == Scope.INSTANCE && !this.permissions.permissions(principal).contains(MANAGE_PERMISSION)) {
			throw new PermissionDeniedException();
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("name", "The name must have 1 to 255 characters.");
		}
		String description = (input.description() != null) ? input.description() : "";
		if (description.length() > DESCRIPTION_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("description", "The description must have at most 1000 characters.");
		}
		if (input.primaryMetatypeId() == null
				|| !this.store.metatypeExists(input.primaryMetatypeId(), PackageImporter.INSTANCE_CONFIGURATION)) {
			throw InvalidCatalogException.invalid("primaryMetatypeId",
					"The primary entity type is not defined in the metamodel.");
		}
		JoinMode joinMode = (input.defaultJoinMode() != null) ? input.defaultJoinMode() : JoinMode.AGGREGATE;
		UUID id;
		try {
			id = this.store.create(new CatalogStore.NewCatalog(name, description, input.primaryMetatypeId(),
					joinMode.dbValue(), input.scope().dbValue()), personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(CatalogStore.NAME_INDEX)) {
				throw InvalidCatalogException.nameTaken();
			}
			throw ex;
		}
		this.audit.append(new AuditEvent("catalog.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				Map.of("name", name, "primaryMetatypeId", input.primaryMetatypeId().toString(), "scope",
						input.scope().dbValue())));
		return Catalog.of(this.store.findVisible(id, personId).orElseThrow());
	}

	/** Instance catalogs and the caller's personal catalogs, ordered by name. */
	@Transactional(readOnly = true)
	public List<Catalog> list(OeaPrincipal principal) {
		return this.store.visibleTo(person(principal)).stream().map(Catalog::of).toList();
	}

	/** The catalog; not existing and another person's personal catalog are answered alike. */
	@Transactional(readOnly = true)
	public Catalog get(OeaPrincipal principal, UUID id) {
		return this.store.findVisible(id, person(principal))
			.map(Catalog::of)
			.orElseThrow(() -> new NoSuchElementException("catalog " + id));
	}

	/** Catalogs belong to persons; other principals hold no permission for them (ADR-032). */
	private static long person(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		return principal.personId();
	}

}
