package io.oea.api.lineage;

import io.oea.api.generated.LineageApi;
import io.oea.api.generated.model.LineageCarriedBy;
import io.oea.api.generated.model.LineageDirection;
import io.oea.api.generated.model.LineageEdge;
import io.oea.api.generated.model.LineageEdgeKind;
import io.oea.api.generated.model.LineageNode;
import io.oea.api.generated.model.LineageQuery;
import io.oea.api.generated.model.LineageResult;
import io.oea.api.generated.model.LineageRole;
import io.oea.api.generated.model.LineageStart;
import io.oea.app.lineage.Lineages;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** The data lineage query (UC-08, US-065, REQ-062; design D4 of us-065-lineage-model-and-query). */
@RestController
public class LineageController implements LineageApi {

	private static final int DEFAULT_MAX_DEPTH = 50;

	private final Lineages lineages;

	public LineageController(Lineages lineages) {
		this.lineages = lineages;
	}

	@Override
	public ResponseEntity<LineageResult> queryLineage(LineageQuery request) {
		Lineages.Direction direction = (request.getDirection() != null)
				? Lineages.Direction.valueOf(request.getDirection().name()) : Lineages.Direction.BOTH;
		Lineages.Result result = this.lineages.query(principal(),
				new Lineages.Query(request.getStartEntityId(), direction,
						(request.getMaxDepth() != null) ? request.getMaxDepth() : DEFAULT_MAX_DEPTH,
						request.getMetatypeIds(), request.getDataObjectId()));
		return ResponseEntity.ok(new LineageResult(
				new LineageStart(result.start().id(), result.start().name(), result.start().metatypeId()),
				LineageDirection.fromValue(result.direction().value()), result.cycleDetected(), result.truncated(),
				result.nodes().stream().map(LineageController::node).toList(),
				result.edges().stream().map(LineageController::edge).toList(), result.paths()));
	}

	private static LineageNode node(Lineages.Node node) {
		return new LineageNode(node.id(), node.name(), node.metatypeId(),
				node.roles().stream().map(LineageRole::fromValue).toList(), node.depth(), node.impact());
	}

	private static LineageEdge edge(Lineages.Edge edge) {
		List<LineageCarriedBy> carried = edge.carriedBy()
			.stream()
			.map((found) -> new LineageCarriedBy(found.connectionId(), found.dataObjectId(), found.dataObjectName()))
			.toList();
		LineageEdge body = new LineageEdge(edge.id(), LineageEdgeKind.fromValue(edge.kind()), edge.metatypeId(),
				edge.sourceId(), edge.targetId(), carried, edge.carriedByTruncated(), edge.transformationRestricted());
		if (edge.transformationType() != null) {
			body.setTransformationType(edge.transformationType());
		}
		return body;
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
