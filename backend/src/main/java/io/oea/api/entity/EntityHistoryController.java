package io.oea.api.entity;

import io.oea.api.generated.EntitiesApi;
import io.oea.api.generated.model.EntityHistoryActor;
import io.oea.api.generated.model.EntityHistoryResponse;
import io.oea.api.generated.model.EntityHistoryResponseCreated;
import io.oea.api.generated.model.EntityVersionChange;
import io.oea.api.generated.model.EntityVersionSummary;
import io.oea.api.generated.model.PageMeta;
import io.oea.app.history.EntityHistory;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * The change history of an entity (US-098, design D4 of us-098-entity-history). The other
 * operations of the interface are still planned and answered with 501 by its defaults.
 */
@RestController
public class EntityHistoryController implements EntitiesApi {

	private final EntityHistory history;

	public EntityHistoryController(EntityHistory history) {
		this.history = history;
	}

	/** No example responses are rendered for the planned operations. */
	@Override
	public Optional<NativeWebRequest> getRequest() {
		return Optional.empty();
	}

	@Override
	public ResponseEntity<EntityHistoryResponse> getEntityHistory(Integer id, OffsetDateTime from, OffsetDateTime to,
			Long actorId, Integer page, Integer pageSize, String cursor) {
		EntityHistory.Page result = this.history.read(principal(), id,
				new EntityHistory.Query((from != null) ? from.toInstant() : null, (to != null) ? to.toInstant() : null,
						actorId, (page != null) ? page : 0, (pageSize != null) ? pageSize : 50, cursor));
		EntityHistoryResponse response = new EntityHistoryResponse().entityId(result.entityId())
			.entityName(result.entityName())
			.actors(result.actors().stream().map(EntityHistoryController::actor).toList())
			.versions(result.versions()
				.stream()
				.map((version) -> new EntityVersionSummary().version(version.version())
					.changedAt(time(version.changedAt()))
					.changedBy(actor(version.changedBy()))
					.changes(version.changes()
						.stream()
						.map((change) -> new EntityVersionChange().field(change.field())
							.label(change.label())
							.isSystemField(change.systemField())
							.restricted(change.restricted()))
						.toList()))
				.toList())
			.page(new PageMeta().page(result.page())
				.pageSize(result.pageSize())
				.totalElements(Math.toIntExact(result.totalElements()))
				.totalPages(result.totalPages()))
			.nextCursor(result.nextCursor());
		if (result.created() != null) {
			response.created(new EntityHistoryResponseCreated().changedAt(time(result.created().changedAt()))
				.changedBy(actor(result.created().changedBy())));
		}
		return ResponseEntity.ok(response);
	}

	private static EntityHistoryActor actor(EntityHistory.Actor actor) {
		return new EntityHistoryActor().id(actor.id()).name(actor.name());
	}

	private static OffsetDateTime time(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
