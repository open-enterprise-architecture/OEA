package io.oea.api.solution;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.SolutionsApi;
import io.oea.api.generated.model.DeltaConflict;
import io.oea.api.generated.model.DeltaConflictResolution;
import io.oea.api.generated.model.DeletionConflict;
import io.oea.api.generated.model.EntityVersionDataType;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PropertyValue;
import io.oea.api.generated.model.PropertyValueValueRef;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.Solution;
import io.oea.api.generated.model.SolutionCreate;
import io.oea.api.generated.model.SolutionDelta;
import io.oea.api.generated.model.SolutionDeltaChange;
import io.oea.api.generated.model.SolutionDeltaConflict;
import io.oea.api.generated.model.SolutionDeltaCounts;
import io.oea.api.generated.model.SolutionDeltaList;
import io.oea.api.generated.model.SolutionDeltaType;
import io.oea.api.generated.model.SolutionDeltaUpdate;
import io.oea.api.generated.model.SolutionDeltaValue;
import io.oea.api.generated.model.SolutionDeltaValueValueRef;
import io.oea.api.generated.model.SolutionDeltaWrite;
import io.oea.api.generated.model.SolutionDiffEntry;
import io.oea.api.generated.model.SolutionDiffField;
import io.oea.api.generated.model.SolutionGoLiveCounts;
import io.oea.api.generated.model.SolutionGoLivePreview;
import io.oea.api.generated.model.SolutionGoLiveResult;
import io.oea.api.generated.model.SolutionGoLiveViolation;
import io.oea.api.generated.model.SolutionLandscapeEntity;
import io.oea.api.generated.model.SolutionLandscapeEntry;
import io.oea.api.generated.model.SolutionLandscapePage;
import io.oea.api.generated.model.SolutionOpenIssue;
import io.oea.api.generated.model.SolutionOpenIssueResolution;
import io.oea.api.generated.model.EntityHistoryActor;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.SolutionSummary;
import io.oea.app.entity.EntityInput;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.SolutionConflicts;
import io.oea.app.solution.SolutionDeltas;
import io.oea.app.solution.SolutionDiff;
import io.oea.app.solution.OpenIssues;
import io.oea.app.solution.SolutionGoLive;
import io.oea.app.solution.SolutionLandscape;
import io.oea.app.solution.Solutions;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Creating and reading solutions, their baseline and their entity deltas (US-038 to US-042). */
@RestController
public class SolutionController implements SolutionsApi {

	/** Path of the solution collection. */
	public static final String SOLUTIONS_PATH = "/api/v1/solutions";

	private final Solutions solutions;

	private final SolutionLandscape landscape;

	private final SolutionDeltas deltas;

	private final SolutionDiff diff;

	private final SolutionGoLive goLive;

	private final OpenIssues openIssues;

	public SolutionController(Solutions solutions, SolutionLandscape landscape, SolutionDeltas deltas,
			SolutionDiff diff, SolutionGoLive goLive, OpenIssues openIssues) {
		this.openIssues = openIssues;
		this.solutions = solutions;
		this.landscape = landscape;
		this.deltas = deltas;
		this.diff = diff;
		this.goLive = goLive;
	}

	@Override
	public ResponseEntity<SolutionGoLivePreview> previewSolutionGoLive(UUID id) {
		SolutionGoLive.Preview preview = this.goLive.preview(principal(), id);
		return ResponseEntity.ok(new SolutionGoLivePreview().solutionId(preview.solutionId())
			.counts(counts(preview.counts()))
			.conflicts(preview.conflicts().stream().map(SolutionController::conflict).toList())
			.deletionConflicts(deletionConflicts(preview.deletionConflicts()))
			.violations(preview.violations().stream().map(SolutionController::violation).toList()));
	}

	static List<DeletionConflict> deletionConflicts(List<SolutionGoLive.DeletionConflict> conflicts) {
		return conflicts.stream()
			.map((conflict) -> new DeletionConflict().deltaId(conflict.deltaId())
				.deltaType(DeletionConflict.DeltaTypeEnum.fromValue(conflict.deltaType()))
				.entityId(conflict.entityId())
				.entityName(conflict.entityName()))
			.toList();
	}

	static SolutionGoLiveViolation violation(SolutionGoLive.Violation violation) {
		return new SolutionGoLiveViolation().deltaId(violation.deltaId())
			.entityId(violation.entityId())
			.entityName(violation.entityName())
			.field(violation.field())
			.label(violation.label())
			.kind(SolutionGoLiveViolation.KindEnum.fromValue(violation.kind()));
	}

	@Override
	public ResponseEntity<SolutionGoLiveResult> goLiveSolution(UUID id) {
		return ResponseEntity.ok(result(this.goLive.goLive(principal(), id)));
	}

	static SolutionGoLiveResult result(SolutionGoLive.Result result) {
		return new SolutionGoLiveResult().solutionId(result.solutionId())
			.status(SolutionStatus.fromValue(result.status()))
			.counts(counts(result.counts()))
			.openIssues(result.openIssues().stream().map(SolutionController::openIssue).toList());
	}

	@Override
	public ResponseEntity<List<SolutionOpenIssue>> listSolutionOpenIssues(UUID id) {
		return ResponseEntity
			.ok(this.openIssues.list(principal(), id).stream().map(SolutionController::openIssue).toList());
	}

	@Override
	public ResponseEntity<List<SolutionOpenIssue>> resolveSolutionOpenIssues(UUID id,
			SolutionOpenIssueResolution request) {
		return ResponseEntity.ok(this.openIssues
			.resolve(principal(), id, request.getResolutions()
				.stream()
				.map((item) -> new OpenIssues.Resolution(item.getIssueId(), item.getValue(),
						Boolean.TRUE.equals(item.getRemove())))
				.toList())
			.stream()
			.map(SolutionController::openIssue)
			.toList());
	}

	@Override
	public ResponseEntity<SolutionDelta> resolveSolutionDeltaConflicts(UUID id, UUID deltaId,
			DeltaConflictResolution request) {
		return ResponseEntity.ok(delta(this.goLive.resolve(principal(), id, deltaId, request.getFields()
			.stream()
			.map((field) -> new SolutionGoLive.Choice(field.getField(), field.getChoice().getValue(), field.getValue()))
			.toList())));
	}

	/** A field conflict of the preview. */
	static DeltaConflict conflict(SolutionGoLive.Conflict conflict) {
		return new DeltaConflict().kind(DeltaConflict.KindEnum.fromValue(conflict.kind()))
			.deltaId(conflict.deltaId())
			.solutionId(conflict.solutionId())
			.entityId(conflict.entityId())
			.entityName(conflict.entityName())
			.field(conflict.field())
			.label(conflict.label())
			.dataType(EntityVersionDataType.fromValue(conflict.dataType()))
			.before(conflict.before())
			.current(conflict.current())
			.after(conflict.after())
			.redacted(conflict.redacted());
	}

	static SolutionGoLiveCounts counts(SolutionGoLive.Counts counts) {
		return new SolutionGoLiveCounts().created(counts.created())
			.modified(counts.modified())
			.retired(counts.retired())
			.retiredConnections(counts.retiredConnections())
			.notInMetamodel(counts.notInMetamodel());
	}

	static SolutionOpenIssue openIssue(OpenIssues.OpenIssue issue) {
		return new SolutionOpenIssue().id(issue.id())
			.entityId(issue.entityId())
			.entityName(issue.entityName())
			.field(issue.field())
			.label(issue.label())
			.dataType(EntityVersionDataType.fromValue(issue.dataType()))
			.kind(SolutionOpenIssue.KindEnum.fromValue(issue.kind()))
			.createdAt(issue.createdAt().atOffset(ZoneOffset.UTC))
			.status(SolutionOpenIssue.StatusEnum.fromValue(issue.status()))
			.resolution((issue.resolution() != null) ? SolutionOpenIssue.ResolutionEnum.fromValue(issue.resolution())
					: null)
			.resolvedAt((issue.resolvedAt() != null) ? issue.resolvedAt().atOffset(ZoneOffset.UTC) : null)
			.resolvedBy((issue.resolvedBy() != null)
					? new EntityHistoryActor().id(issue.resolvedBy().id()).name(issue.resolvedBy().name()) : null)
			.currentValue(issue.currentValue())
			.keptValue(issue.keptValue())
			.removable(issue.removable())
			.redacted(issue.redacted());
	}

	@Override
	@ContractViolationResponse(title = "Invalid solution", detail = "The name or the architecture vision is invalid.")
	public ResponseEntity<Solution> createSolution(SolutionCreate request) {
		Solutions.Solution created = this.solutions.create(principal(),
				new Solutions.NewSolution(request.getName(), request.getDescription(), request.getFromPlateauId(),
						request.getToPlateauId()));
		return ResponseEntity.created(URI.create(SOLUTIONS_PATH + "/" + created.id())).body(solution(created, 0));
	}

	@Override
	public ResponseEntity<List<SolutionSummary>> listSolutions() {
		return ResponseEntity.ok(this.solutions.list().stream().map(SolutionController::summary).toList());
	}

	@Override
	public ResponseEntity<Solution> getSolution(UUID id) {
		Solutions.Solution solution = this.solutions.get(id);
		return ResponseEntity.ok(solution(solution, this.openIssues.openCount(id)));
	}

	@Override
	public ResponseEntity<SolutionLandscapePage> getSolutionLandscape(UUID id, String q, UUID metatypeId,
			Integer page, Integer pageSize, String cursor) {
		SolutionLandscape.Page result = this.landscape.read(id, new SolutionLandscape.Query(q, metatypeId,
				(page != null) ? page : 0, (pageSize != null) ? pageSize : 50, cursor));
		return ResponseEntity.ok(new SolutionLandscapePage()
			.items(result.items()
				.stream()
				.map((entry) -> new SolutionLandscapeEntry().id(entry.id())
					.name(entry.name())
					.metatypeId(entry.metatypeId())
					.metatypeName(entry.metatypeName())
					.version(entry.version())
					.deltaType(deltaType(entry.deltaType()))
					.parallelChanges(conflicts(entry.parallelChanges()))
					.lifecycle(SolutionLandscapeEntry.LifecycleEnum.fromValue(entry.lifecycle()))
					.changedBy(changedBy(entry.changedBy()))
					.deltaId(entry.deltaId()))
				.toList())
			.page(new PageMeta().page(result.page())
				.pageSize(result.pageSize())
				.totalElements(Math.toIntExact(result.totalElements()))
				.totalPages(result.totalPages()))
			.nextCursor(result.nextCursor()));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid delta", detail = "The delta is invalid.")
	public ResponseEntity<SolutionDelta> createSolutionDelta(UUID id, SolutionDeltaWrite request) {
		SolutionDeltas.Delta created = this.deltas.create(principal(), id,
				new SolutionDeltas.NewDelta(type(request.getDeltaType()), request.getEntityId(),
						request.getMetatypeId(), request.getName(), request.getDescription(),
						properties(request.getProperties())));
		return ResponseEntity.status(201).body(delta(created));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid delta", detail = "The delta is invalid.")
	public ResponseEntity<SolutionDelta> updateSolutionDelta(UUID id, UUID deltaId, SolutionDeltaUpdate request) {
		return ResponseEntity.ok(delta(this.deltas.update(principal(), id, deltaId,
				new SolutionDeltas.Modification(request.getName(), request.getDescription(),
						properties(request.getProperties())))));
	}

	@Override
	public ResponseEntity<SolutionLandscapeEntity> getSolutionLandscapeEntity(UUID id, Integer entityId) {
		SolutionLandscape.EntityEntry found = this.landscape.entry(principal(), id, entityId);
		return ResponseEntity.ok(new SolutionLandscapeEntity().id(found.entry().id())
			.name(found.entry().name())
			.metatypeId(found.entry().metatypeId())
			.metatypeName(found.entry().metatypeName())
			.version(found.entry().version())
			.deltaType(deltaType(found.entry().deltaType()))
			.parallelChanges(conflicts(found.entry().parallelChanges()))
			.lifecycle(SolutionLandscapeEntity.LifecycleEnum.fromValue(found.entry().lifecycle()))
			.changedBy(changedBy(found.entry().changedBy()))
			.deltaId(found.entry().deltaId())
			.description(found.description())
			.properties(found.properties().stream().map(SolutionController::value).toList()));
	}

	private static io.oea.api.generated.model.SolutionLandscapeEntryChangedBy changedBy(
			SolutionLandscape.ChangedBy changedBy) {
		return (changedBy != null) ? new io.oea.api.generated.model.SolutionLandscapeEntryChangedBy()
			.solutionId(changedBy.solutionId())
			.name(changedBy.name()) : null;
	}

	private static SolutionDeltaType deltaType(SolutionDeltas.Type type) {
		return (type != null) ? SolutionDeltaType.fromValue(type.value()) : null;
	}

	private static SolutionDeltas.Type type(SolutionDeltaType type) {
		return switch (type) {
			case NEW -> SolutionDeltas.Type.NEW;
			case MODIFIED -> SolutionDeltas.Type.MODIFIED;
			case RETIRING -> SolutionDeltas.Type.RETIRING;
		};
	}

	private static List<EntityInput.PropertyInput> properties(
			List<io.oea.api.generated.model.PropertyWriteValue> properties) {
		return (properties == null) ? List.of()
				: properties.stream()
					.map((property) -> new EntityInput.PropertyInput(property.getPropertyDefId(), property.getValue()))
					.toList();
	}

	@Override
	public ResponseEntity<SolutionDeltaList> listSolutionDeltas(UUID id, Integer page, Integer pageSize, String cursor) {
		SolutionDeltas.Page result = this.deltas.list(principal(), id, pageQuery(page, pageSize, cursor));
		return ResponseEntity.ok(new SolutionDeltaList()
			.items(result.items().stream().map(SolutionController::delta).toList())
			.page(pageMeta(result))
			.counts(counts(result.counts()))
			.conflicted(Math.toIntExact(result.conflicted()))
			.nextCursor(result.nextCursor()));
	}

	@Override
	public ResponseEntity<io.oea.api.generated.model.SolutionDiff> getSolutionDiff(UUID id, Integer page,
			Integer pageSize, String cursor) {
		SolutionDiff.Page result = this.diff.read(principal(), id, pageQuery(page, pageSize, cursor));
		return ResponseEntity.ok(new io.oea.api.generated.model.SolutionDiff()
			.entries(result.entries().stream().map(SolutionController::diffEntry).toList())
			.page(pageMeta(result.deltas()))
			.counts(counts(result.deltas().counts()))
			.nextCursor(result.deltas().nextCursor()));
	}

	private static SolutionDiffEntry diffEntry(SolutionDiff.Entry entry) {
		return new SolutionDiffEntry().deltaId(entry.deltaId())
			.deltaType(SolutionDeltaType.fromValue(entry.type().value()))
			.entityId(entry.entityId())
			.metatypeId(entry.metatypeId())
			.metatypeName(entry.metatypeName())
			.name(entry.name())
			.inCatalog(entry.inCatalog())
			.fields(entry.fields().stream().map(SolutionController::diffField).toList())
			.conflicts(conflicts(entry.conflicts()));
	}

	private static List<SolutionDeltaConflict> conflicts(List<SolutionConflicts.Conflict> conflicts) {
		return conflicts.stream()
			.map((conflict) -> new SolutionDeltaConflict().solutionId(conflict.solutionId())
				.solutionName(conflict.solutionName())
				.status(SolutionStatus.fromValue(conflict.status()))
				.ownerId(conflict.ownerId())
				.ownerName(conflict.ownerName())
				.deltaType(SolutionDeltaType.fromValue(conflict.deltaType().value())))
			.toList();
	}

	private static SolutionDiffField diffField(SolutionDiff.Field field) {
		return new SolutionDiffField().field(SolutionDiffField.FieldEnum.fromValue(field.field()))
			.propertyDefId(field.propertyDefId())
			.propertyName(field.propertyName())
			.dataType((field.dataType() != null) ? SolutionDiffField.DataTypeEnum.fromValue(field.dataType()) : null)
			.redacted(field.redacted())
			.current(typed(field.current()))
			.target(typed(field.target()))
			.changed(field.changed())
			.catalogChanged(field.catalogChanged());
	}

	static SolutionDeltas.PageQuery pageQuery(Integer page, Integer pageSize, String cursor) {
		return new SolutionDeltas.PageQuery((page != null) ? page : 0, (pageSize != null) ? pageSize : 50, cursor);
	}

	static PageMeta pageMeta(SolutionDeltas.Page page) {
		return new PageMeta().page(page.page())
			.pageSize(page.pageSize())
			.totalElements(Math.toIntExact(page.counts().total()))
			.totalPages(page.totalPages());
	}

	static SolutionDeltaCounts counts(SolutionDeltas.Counts counts) {
		return new SolutionDeltaCounts()._new(Math.toIntExact(counts.newCount()))
			.modified(Math.toIntExact(counts.modified()))
			.retiring(Math.toIntExact(counts.retiring()));
	}

	@Override
	public ResponseEntity<Void> deleteSolutionDelta(UUID id, UUID deltaId) {
		this.deltas.delete(principal(), id, deltaId);
		return ResponseEntity.noContent().build();
	}

	private static SolutionDelta delta(SolutionDeltas.Delta delta) {
		return new SolutionDelta().id(delta.id())
			.deltaType(SolutionDeltaType.fromValue(delta.type().value()))
			.entityId(delta.entityId())
			.name(delta.name())
			.metatypeId(delta.metatypeId())
			.metatypeName(delta.metatypeName())
			.description(delta.description())
			.properties(delta.properties().stream().map(SolutionController::value).toList())
			.changes(delta.changes().stream().map(SolutionController::change).toList())
			.conflicts(conflicts(delta.conflicts()))
			.createdAt(delta.createdAt().atOffset(ZoneOffset.UTC))
			.updatedAt((delta.updatedAt() != null) ? delta.updatedAt().atOffset(ZoneOffset.UTC) : null)
			.entityDeleted(delta.entityDeleted());
	}

	private static SolutionDeltaChange change(SolutionDeltas.Change change) {
		return new SolutionDeltaChange().field(SolutionDeltaChange.FieldEnum.fromValue(change.field()))
			.propertyDefId(change.propertyDefId())
			.propertyName(change.propertyName())
			.dataType((change.dataType() != null) ? SolutionDeltaChange.DataTypeEnum.fromValue(change.dataType()) : null)
			.redacted(change.redacted())
			.before(typed(change.before()))
			.after(typed(change.after()));
	}

	private static SolutionDeltaValue typed(SolutionDeltas.Typed value) {
		if (value == null) {
			return null;
		}
		return new SolutionDeltaValue().valueText(value.text())
			.valueInt(value.integer())
			.valueBool(value.bool())
			.valueDate(value.date())
			.valueEnum(value.enumValue())
			.valueRef((value.reference() != null) ? new SolutionDeltaValueValueRef().id(value.reference().id())
				.name(value.reference().name())
				.metatypeAbbr(value.reference().metatypeAbbreviation()) : null);
	}

	private static PropertyValue value(SolutionDeltas.Value value) {
		PropertyValue result = new PropertyValue().propertyDefId(value.propertyDefId())
			.propertyName(value.propertyName())
			.dataType(PropertyValue.DataTypeEnum.fromValue(value.dataType()))
			.redacted(value.redacted());
		SolutionDeltas.Typed typed = value.typed();
		if (typed != null) {
			result.valueText(typed.text())
				.valueInt(typed.integer())
				.valueBool(typed.bool())
				.valueDate(typed.date())
				.valueEnum(typed.enumValue());
			if (typed.reference() != null) {
				result.valueRef(new PropertyValueValueRef().id(typed.reference().id())
					.name(typed.reference().name())
					.metatypeAbbr(typed.reference().metatypeAbbreviation()));
			}
		}
		return result;
	}

	private static SolutionSummary summary(Solutions.Solution solution) {
		return new SolutionSummary().id(solution.id())
			.name(solution.name())
			.status(SolutionStatus.fromValue(solution.status()))
			.ownerId(solution.ownerId())
			.ownerName(solution.ownerName())
			.createdAt(solution.createdAt().atOffset(ZoneOffset.UTC));
	}

	private static Solution solution(Solutions.Solution solution, int openIssueCount) {
		return new Solution().id(solution.id())
			.openIssueCount(openIssueCount)
			.name(solution.name())
			.status(SolutionStatus.fromValue(solution.status()))
			.ownerId(solution.ownerId())
			.ownerName(solution.ownerName())
			.createdAt(solution.createdAt().atOffset(ZoneOffset.UTC))
			.description(solution.description())
			.fromPlateauId(solution.fromPlateauId())
			.toPlateauId(solution.toPlateauId())
			.createdBy(new PrincipalRef(PrincipalRef.PrincipalTypeEnum.PERSON, solution.createdByName())
				.id(Math.toIntExact(solution.createdById())));
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
