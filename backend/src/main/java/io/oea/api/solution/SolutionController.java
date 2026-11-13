package io.oea.api.solution;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.SolutionsApi;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PropertyValue;
import io.oea.api.generated.model.PropertyValueValueRef;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.Solution;
import io.oea.api.generated.model.SolutionCreate;
import io.oea.api.generated.model.SolutionDelta;
import io.oea.api.generated.model.SolutionDeltaChange;
import io.oea.api.generated.model.SolutionDeltaCounts;
import io.oea.api.generated.model.SolutionDeltaList;
import io.oea.api.generated.model.SolutionDeltaType;
import io.oea.api.generated.model.SolutionDeltaUpdate;
import io.oea.api.generated.model.SolutionDeltaValue;
import io.oea.api.generated.model.SolutionDeltaValueValueRef;
import io.oea.api.generated.model.SolutionDeltaWrite;
import io.oea.api.generated.model.SolutionDiffEntry;
import io.oea.api.generated.model.SolutionDiffField;
import io.oea.api.generated.model.SolutionLandscapeEntity;
import io.oea.api.generated.model.SolutionLandscapeEntry;
import io.oea.api.generated.model.SolutionLandscapePage;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.SolutionSummary;
import io.oea.app.entity.EntityInput;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.SolutionDeltas;
import io.oea.app.solution.SolutionDiff;
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

	public SolutionController(Solutions solutions, SolutionLandscape landscape, SolutionDeltas deltas,
			SolutionDiff diff) {
		this.solutions = solutions;
		this.landscape = landscape;
		this.deltas = deltas;
		this.diff = diff;
	}

	@Override
	@ContractViolationResponse(title = "Invalid solution", detail = "The name or the architecture vision is invalid.")
	public ResponseEntity<Solution> createSolution(SolutionCreate request) {
		Solutions.Solution created = this.solutions.create(principal(),
				new Solutions.NewSolution(request.getName(), request.getDescription()));
		return ResponseEntity.created(URI.create(SOLUTIONS_PATH + "/" + created.id())).body(solution(created));
	}

	@Override
	public ResponseEntity<List<SolutionSummary>> listSolutions() {
		return ResponseEntity.ok(this.solutions.list().stream().map(SolutionController::summary).toList());
	}

	@Override
	public ResponseEntity<Solution> getSolution(UUID id) {
		return ResponseEntity.ok(solution(this.solutions.get(id)));
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
					.deltaType(deltaType(entry.deltaType())))
				.toList())
			.page(new PageMeta().page(result.page())
				.pageSize(result.pageSize())
				.totalElements(Math.toIntExact(result.totalElements()))
				.totalPages(result.totalPages()))
			.nextCursor(result.nextCursor()));
	}

	@Override
	@ContractViolationResponse(title = "Invalid delta", detail = "The delta is invalid.")
	public ResponseEntity<SolutionDelta> createSolutionDelta(UUID id, SolutionDeltaWrite request) {
		SolutionDeltas.Delta created = this.deltas.create(principal(), id,
				new SolutionDeltas.NewDelta(type(request.getDeltaType()), request.getEntityId(),
						request.getMetatypeId(), request.getName(), request.getDescription(),
						properties(request.getProperties())));
		return ResponseEntity.status(201).body(delta(created));
	}

	@Override
	@ContractViolationResponse(title = "Invalid delta", detail = "The delta is invalid.")
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
			.description(found.description())
			.properties(found.properties().stream().map(SolutionController::value).toList()));
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
			.fields(entry.fields().stream().map(SolutionController::diffField).toList());
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
			.createdAt(delta.createdAt().atOffset(ZoneOffset.UTC))
			.updatedAt((delta.updatedAt() != null) ? delta.updatedAt().atOffset(ZoneOffset.UTC) : null);
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
		result.valueText(value.text())
			.valueInt(value.integer())
			.valueBool(value.bool())
			.valueDate(value.date())
			.valueEnum(value.enumValue());
		if (value.reference() != null) {
			result.valueRef(new PropertyValueValueRef().id(value.reference().id())
				.name(value.reference().name())
				.metatypeAbbr(value.reference().metatypeAbbreviation()));
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

	private static Solution solution(Solutions.Solution solution) {
		return new Solution().id(solution.id())
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
