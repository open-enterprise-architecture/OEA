package io.oea.api.entity;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.EntitiesApi;
import io.oea.api.generated.model.EntityHistoryActor;
import io.oea.api.generated.model.EntityHistoryResponse;
import io.oea.api.generated.model.EntityHistoryResponseCreated;
import io.oea.api.generated.model.EntityVersionChange;
import io.oea.api.generated.model.EntityVersionDataType;
import io.oea.api.generated.model.EntityVersionDiff;
import io.oea.api.generated.model.EntityVersionField;
import io.oea.api.generated.model.EntityVersionState;
import io.oea.api.generated.model.EntityVersionSummary;
import io.oea.api.generated.model.FieldDiff;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.RestoreConnection;
import io.oea.api.generated.model.RestoreField;
import io.oea.api.generated.model.RestoreFieldRef;
import io.oea.api.generated.model.RestorePreview;
import io.oea.api.generated.model.RestoreRequest;
import io.oea.api.generated.model.RestoreResult;
import io.oea.api.generated.model.RestoredField;
import io.oea.api.generated.model.ValidationError;
import io.oea.app.entity.Issue;
import io.oea.app.history.EntityHistory;
import io.oea.app.history.EntityRestore;
import io.oea.app.history.EntityVersions;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * The change history of an entity (US-098, design D4 of us-098-entity-history) and the values
 * of its versions (US-099, US-100, design D4 of us-099-version-diff), and the restore of an entity
 * to an earlier version (US-101 to US-103, design D3 of us-101-entity-restore). The other operations of
 * the interface are still planned and answered with 501 by its defaults.
 */
@RestController
public class EntityHistoryController implements EntitiesApi {

	private final EntityHistory history;

	private static final Pattern STRONG_VERSION = Pattern.compile("\"(\\d{1,9})\"");

	private final EntityVersions versions;

	private final EntityRestore restore;

	public EntityHistoryController(EntityHistory history, EntityVersions versions, EntityRestore restore) {
		this.history = history;
		this.versions = versions;
		this.restore = restore;
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
			.currentVersion(result.currentVersion())
			.actors(result.actors().stream().map(EntityHistoryController::actor).toList())
			.versions(result.versions()
				.stream()
				.map((version) -> new EntityVersionSummary().version(version.version())
					.restoredFromVersion(version.restoredFromVersion())
					.restoredFields((version.restoredFields() != null) ? version.restoredFields()
						.stream()
						.map((field) -> new RestoredField().field(field.field())
							.label(field.label())
							.restricted(field.restricted()))
						.toList() : null)
					.reason(version.reason())
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

	@Override
	public ResponseEntity<EntityVersionState> getEntityVersion(Integer id, Integer version) {
		EntityVersions.State state = this.versions.state(principal(), id, version);
		return ResponseEntity.ok(new EntityVersionState().entityId(state.entityId())
			.entityName(state.entityName())
			.version(state.version())
			.changedAt(time(state.changedAt()))
			.changedBy(actor(state.changedBy()))
			.fields(state.fields()
				.stream()
				.map((field) -> new EntityVersionField().field(field.field())
					.label(field.label())
					.isSystemField(field.systemField())
					.dataType(EntityVersionDataType.fromValue(field.dataType()))
					.value(field.value())
					.restricted(field.restricted()))
				.toList()));
	}

	@Override
	public ResponseEntity<EntityVersionDiff> compareEntityVersions(Integer id, Integer from, Integer to) {
		EntityVersions.Diff diff = this.versions.compare(principal(), id, from, to);
		return ResponseEntity.ok(new EntityVersionDiff().entityId(diff.entityId())
			.from(diff.from())
			.to(diff.to())
			.changes(diff.changes()
				.stream()
				.map((change) -> new FieldDiff().field(change.field())
					.label(change.label())
					.isSystemField(change.systemField())
					.dataType(EntityVersionDataType.fromValue(change.dataType()))
					.oldValue(change.oldValue())
					.newValue(change.newValue())
					.redacted(change.redacted()))
				.toList()));
	}

	@Override
	@ContractViolationResponse(title = "Invalid query", detail = "The target version is invalid.")
	public ResponseEntity<RestorePreview> previewEntityRestore(Integer id, Integer targetVersion) {
		EntityRestore.Preview preview = this.restore.preview(principal(), id, targetVersion);
		return ResponseEntity.ok(new RestorePreview().entityId(preview.entityId())
			.currentVersion(preview.currentVersion())
			.targetVersion(preview.targetVersion())
			.fields(preview.fields()
				.stream()
				.map((field) -> new RestoreField().field(field.field())
					.label(field.label())
					.isSystemField(field.systemField())
					.dataType(EntityVersionDataType.fromValue(field.dataType()))
					.currentValue(field.currentValue())
					.targetValue(field.targetValue())
					.redacted(field.redacted())
					.restore(field.restore())
					.reason((field.reason() != null) ? RestoreField.ReasonEnum.fromValue(field.reason()) : null))
				.toList())
			.warnings(warnings(preview.warnings()))
			.connections(preview.connections()
				.stream()
				.map((connection) -> new RestoreConnection().entityId(connection.entityId())
					.name(connection.name())
					.otherEndpoint(connection.otherEndpoint())
					.direction(connection.outgoing() ? RestoreConnection.DirectionEnum.OUTGOING
							: RestoreConnection.DirectionEnum.INCOMING)
					.change(connection.added() ? RestoreConnection.ChangeEnum.ADDED : RestoreConnection.ChangeEnum.CHANGED))
				.toList()));
	}

	@Override
	@ContractViolationResponse(title = "Invalid request", detail = "Some fields do not meet the contract.")
	public ResponseEntity<RestoreResult> restoreEntity(Integer id, String ifMatch, RestoreRequest restoreRequest) {
		EntityRestore.Result result = this.restore.restore(principal(), id, restoreRequest.getTargetVersion(),
				expectedVersion(ifMatch), restoreRequest.getReason(), restoreRequest.getFields());
		return ResponseEntity.ok(new RestoreResult().entityId(result.entityId())
			.version(result.version())
			.restoredFromVersion(result.restoredFromVersion())
			.restored(result.restored().stream().map(EntityHistoryController::fieldRef).toList())
			.notRestored(result.notRestored().stream().map(EntityHistoryController::fieldRef).toList())
			.warnings(warnings(result.warnings())));
	}

	/**
	 * The version named by {@code If-Match}: {@code null} without one (428); {@code -1} for a
	 * value that is not a strong ETag of a version, which matches no version (412).
	 */
	static Long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			return null;
		}
		Matcher matcher = STRONG_VERSION.matcher(ifMatch.strip());
		return matcher.matches() ? Long.parseLong(matcher.group(1)) : -1L;
	}

	private static RestoreFieldRef fieldRef(EntityRestore.FieldRef field) {
		return new RestoreFieldRef().field(field.field())
			.label(field.label())
			.reason((field.reason() != null) ? RestoreFieldRef.ReasonEnum.fromValue(field.reason()) : null);
	}

	private static List<ValidationError> warnings(List<Issue> issues) {
		return issues.stream()
			.map((issue) -> new ValidationError().field(issue.field()).code(issue.code()).message(issue.message()))
			.toList();
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
