package io.oea.api.solution;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.SolutionsApi;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.Solution;
import io.oea.api.generated.model.SolutionCreate;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.SolutionSummary;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.Solutions;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Creating and reading solutions (US-038; design D3 of us-038-create-solution). */
@RestController
public class SolutionController implements SolutionsApi {

	/** Path of the solution collection. */
	public static final String SOLUTIONS_PATH = "/api/v1/solutions";

	private final Solutions solutions;

	public SolutionController(Solutions solutions) {
		this.solutions = solutions;
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
