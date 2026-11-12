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
import io.oea.api.generated.model.SolutionDeltaList;
import io.oea.api.generated.model.SolutionDeltaType;
import io.oea.api.generated.model.SolutionDeltaWrite;
import io.oea.api.generated.model.SolutionLandscapeEntry;
import io.oea.api.generated.model.SolutionLandscapePage;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.SolutionSummary;
import io.oea.app.entity.EntityInput;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.SolutionDeltas;
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

	public SolutionController(Solutions solutions, SolutionLandscape landscape, SolutionDeltas deltas) {
		this.solutions = solutions;
		this.landscape = landscape;
		this.deltas = deltas;
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
					.version(entry.version()))
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
				new SolutionDeltas.NewDelta(
						(request.getDeltaType() == SolutionDeltaType.NEW) ? SolutionDeltas.Type.NEW
								: SolutionDeltas.Type.RETIRING,
						request.getEntityId(), request.getMetatypeId(), request.getName(), request.getDescription(),
						request.getProperties()
							.stream()
							.map((property) -> new EntityInput.PropertyInput(property.getPropertyDefId(),
									property.getValue()))
							.toList()));
		return ResponseEntity.status(201).body(delta(created));
	}

	@Override
	public ResponseEntity<SolutionDeltaList> listSolutionDeltas(UUID id) {
		return ResponseEntity.ok(new SolutionDeltaList()
			.items(this.deltas.list(principal(), id).stream().map(SolutionController::delta).toList()));
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
			.createdAt(delta.createdAt().atOffset(ZoneOffset.UTC));
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
