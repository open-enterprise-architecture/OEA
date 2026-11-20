package io.oea.api.solution;

import io.oea.api.generated.PlateausApi;
import io.oea.api.generated.model.EntityHistoryActor;
import io.oea.api.generated.model.Plateau;
import io.oea.api.generated.model.PlateauBasis;
import io.oea.api.generated.model.PlateauCreate;
import io.oea.api.generated.model.PlateauGoLivePreview;
import io.oea.api.generated.model.PlateauGoLivePreviewWarnings;
import io.oea.api.generated.model.PlateauGoLiveRequest;
import io.oea.api.generated.model.PlateauGoLiveResult;
import io.oea.api.generated.model.PlateauGoLiveSolution;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.PlateauStatus;
import io.oea.api.generated.model.PlateauUpdate;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.PlateauGoLive;
import io.oea.app.solution.Plateaus;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Creating, reading and editing plateaus (US-084, US-085, US-089, US-155; design D2 of us-084-plateaus). */
@RestController
public class PlateauController implements PlateausApi {

	/** Path of the plateau collection. */
	public static final String PLATEAUS_PATH = "/api/v1/plateaus";

	private final Plateaus plateaus;

	private final PlateauGoLive goLive;

	public PlateauController(Plateaus plateaus, PlateauGoLive goLive) {
		this.plateaus = plateaus;
		this.goLive = goLive;
	}

	@Override
	public ResponseEntity<PlateauGoLivePreview> previewPlateauGoLive(UUID id) {
		PlateauGoLive.Preview preview = this.goLive.preview(principal(), id);
		return ResponseEntity.ok(new PlateauGoLivePreview().plateauId(preview.plateauId())
			.baselineId(preview.baselineId())
			.counts(SolutionController.counts(preview.counts()))
			.solutions(preview.solutions()
				.stream()
				.map((solution) -> new PlateauGoLiveSolution().id(solution.id())
					.name(solution.name())
					.status(SolutionStatus.fromValue(solution.status()))
					.counts(SolutionController.counts(solution.preview().counts()))
					.conflicts(solution.preview().conflicts().stream().map(SolutionController::conflict).toList())
					.deletionConflicts(SolutionController.deletionConflicts(solution.preview().deletionConflicts())))
				.toList())
			.violations(preview.violations().stream().map(SolutionController::violation).toList())
			.warnings(new PlateauGoLivePreviewWarnings().notApproved(preview.notApproved())
				.openIssues(preview.openIssues())));
	}

	@Override
	public ResponseEntity<PlateauGoLiveResult> goLivePlateau(UUID id, PlateauGoLiveRequest request) {
		PlateauGoLive.Result result = this.goLive.goLive(principal(), id, request.getName());
		return ResponseEntity.ok(new PlateauGoLiveResult().baseline(plateau(result.baseline()))
			.realized(plateau(result.realized()))
			.solutions(result.solutions().stream().map(SolutionController::result).toList()));
	}

	@Override
	public ResponseEntity<List<Plateau>> listPlateaus() {
		return ResponseEntity.ok(this.plateaus.list().stream().map(PlateauController::plateau).toList());
	}

	@Override
	public ResponseEntity<Plateau> createPlateau(PlateauCreate request) {
		Plateaus.Plateau created = this.plateaus.create(principal(),
				new Plateaus.NewPlateau(request.getName(), request.getDescription(), request.getStatus().getValue(),
						request.getSucceeds(), (request.getBasis() != null) ? request.getBasis().getValue() : null,
						request.getValidFrom()));
		return ResponseEntity.created(URI.create(PLATEAUS_PATH + "/" + created.id())).body(plateau(created));
	}

	@Override
	public ResponseEntity<Plateau> getPlateau(UUID id) {
		return ResponseEntity.ok(plateau(this.plateaus.get(id)));
	}

	@Override
	public ResponseEntity<Plateau> updatePlateau(UUID id, PlateauUpdate request) {
		return ResponseEntity.ok(plateau(this.plateaus.update(principal(), id,
				new Plateaus.PlateauChange(request.getName(), request.getDescription(),
						(request.getStatus() != null) ? request.getStatus().getValue() : null, request.getSucceeds(),
						(request.getBasis() != null) ? request.getBasis().getValue() : null, request.getValidFrom()))));
	}

	static Plateau plateau(Plateaus.Plateau row) {
		return new Plateau().id(row.id())
			.name(row.name())
			.description(row.description())
			.status(PlateauStatus.fromValue(row.status()))
			.succeeds(row.succeeds())
			.basis((row.basis() != null) ? PlateauBasis.fromValue(row.basis()) : null)
			.validFrom(row.validFrom())
			.validTo(row.validTo())
			.solutionCount(row.solutionCount())
			.createdAt(row.createdAt().atOffset(ZoneOffset.UTC))
			.createdBy(new EntityHistoryActor(row.createdById(), row.createdByName()))
			.updatedAt((row.updatedAt() != null) ? row.updatedAt().atOffset(ZoneOffset.UTC) : null);
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
