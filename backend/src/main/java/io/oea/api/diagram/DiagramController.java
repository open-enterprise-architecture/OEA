package io.oea.api.diagram;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.DiagramsApi;
import io.oea.api.generated.model.DeletedDiagram;
import io.oea.api.generated.model.DeletedDiagramPage;
import io.oea.api.generated.model.Diagram;
import io.oea.api.generated.model.DiagramCreate;
import io.oea.api.generated.model.DiagramConnectionOffer;
import io.oea.api.generated.model.DiagramConnectionOfferList;
import io.oea.api.generated.model.DiagramConnectionQuery;
import io.oea.api.generated.model.DiagramEdge;
import io.oea.api.generated.model.DiagramEdgeConnection;
import io.oea.api.generated.model.DiagramPoint;
import io.oea.api.generated.model.DiagramChildConnection;
import io.oea.api.generated.model.DiagramEntity;
import io.oea.api.generated.model.DiagramEntityChild;
import io.oea.api.generated.model.DiagramEntityChildList;
import io.oea.api.generated.model.DiagramHierarchyLine;
import io.oea.api.generated.model.DiagramEntityDetails;
import io.oea.api.generated.model.DiagramEntityField;
import io.oea.api.generated.model.PropertyValue;
import io.oea.api.generated.model.PropertyValueValueRef;
import io.oea.api.generated.model.DiagramEntityPage;
import io.oea.api.generated.model.DiagramPage;
import io.oea.api.generated.model.DiagramShape;
import io.oea.api.generated.model.DiagramShapeEntity;
import io.oea.api.generated.model.DiagramView;
import io.oea.api.generated.model.DiagramViewWrite;
import io.oea.api.generated.model.MetatypeSummary;
import io.oea.api.generated.model.DiagramUpdate;
import io.oea.api.generated.model.DiagramViewpoint;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.ViewpointNotation;
import io.oea.api.entity.EntityBatchController;
import io.oea.api.metamodel.ViewpointController;
import io.oea.app.diagram.Diagrams;
import io.oea.app.metamodel.PropertyVisibility;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.app.diagramview.DiagramViews;
import io.oea.audit.AuditEvent;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Diagrams as objects (US-074, SCR-067; design D2 and D8 of us-074-diagrams). */
@RestController
public class DiagramController implements DiagramsApi {

	/** Path of the diagram collection. */
	public static final String DIAGRAMS_PATH = "/api/v1/diagrams";

	private static final Pattern STRONG_VERSION = Pattern.compile("\"(\\d{1,9})\"");

	private final Diagrams diagrams;

	private final DiagramViews views;

	private final PropertyVisibility visibility;

	public DiagramController(Diagrams diagrams, DiagramViews views, PropertyVisibility visibility) {
		this.diagrams = diagrams;
		this.views = views;
		this.visibility = visibility;
	}

	@Override
	public ResponseEntity<DiagramPage> listDiagrams(String q, Integer page, Integer pageSize, String cursor) {
		Diagrams.Page result = this.diagrams.list(new Diagrams.Query(q, page(page), size(pageSize), cursor), false);
		return ResponseEntity.ok(new DiagramPage(result.items().stream().map(DiagramController::diagram).toList(),
				meta(result), result.nextCursor()));
	}

	@Override
	public ResponseEntity<DeletedDiagramPage> listDeletedDiagrams(String q, Integer page, Integer pageSize,
			String cursor) {
		Diagrams.Page result = this.diagrams.list(new Diagrams.Query(q, page(page), size(pageSize), cursor), true);
		return ResponseEntity.ok(new DeletedDiagramPage(
				result.items().stream().map(DiagramController::deleted).toList(), meta(result), result.nextCursor()));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid diagram", detail = "Some fields are invalid.")
	public ResponseEntity<Diagram> createDiagram(DiagramCreate request) {
		Diagram created = diagram(this.diagrams.create(principal(), request.getName(), request.getDescription(),
				request.getViewpointId()));
		return ResponseEntity.created(URI.create(DIAGRAMS_PATH + "/" + created.getId()))
			.eTag(etag(created.getVersion()))
			.body(created);
	}

	@Override
	public ResponseEntity<Diagram> getDiagram(UUID id) {
		return versioned(diagram(this.diagrams.get(id)));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid diagram", detail = "Some fields are invalid.")
	public ResponseEntity<Diagram> updateDiagram(UUID id, String ifMatch, DiagramUpdate request) {
		return versioned(diagram(this.diagrams.update(principal(), id, request.getName(), request.getDescription(),
				expectedVersion(ifMatch))));
	}

	@Override
	public ResponseEntity<Void> deleteDiagram(UUID id, String ifMatch) {
		this.diagrams.delete(principal(), id, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Diagram> restoreDiagram(UUID id, String ifMatch) {
		return versioned(diagram(this.diagrams.restore(principal(), id, expectedVersion(ifMatch))));
	}

	@Override
	public ResponseEntity<DiagramView> getDiagramView(UUID id) {
		return view(this.views.view(id));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "PropertyVisibility.requireScopeAssignment: a new connection of a scoping type "
			+ "needs visibility-scope:assign; the whole save is rejected before the version check and every 422",
			probeRole = "integration-writer", probePath = "/api/v1/views/diagrams/00000000-0000-4000-8000-0000000d1a01",
			probeBody = """
					{"shapes": [], "newConnections": [{"key": "00000000-0000-4000-8000-0000000c0e01",
					 "metatypeId": "00000000-0000-4000-8000-0000000c0de1"}]}""")
	public ResponseEntity<DiagramView> saveDiagramView(UUID id, String ifMatch, DiagramViewWrite request,
			String xAgentId, String xAgentRun) {
		List<DiagramViews.ShapeInput> shapes = request.getShapes()
			.stream()
			.map((shape) -> new DiagramViews.ShapeInput(shape.getId(), shape.getEntityId(), shape.getNewEntityKey(),
					shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight(), shape.getParentShapeId()))
			.toList();
		List<DiagramViews.NewEntityInput> newEntities = (request.getNewEntities() == null) ? List.of()
				: request.getNewEntities()
					.stream()
					.map((entity) -> new DiagramViews.NewEntityInput(entity.getKey(), entity.getMetatypeId(),
							entity.getName(), entity.getDescription(), properties(entity.getProperties()),
							entity.getParentEntityId(), entity.getParentNewEntityKey()))
					.toList();
		List<DiagramViews.EntityUpdateInput> updates = (request.getEntityUpdates() == null) ? List.of()
				: request.getEntityUpdates()
					.stream()
					.map((update) -> new DiagramViews.EntityUpdateInput(update.getEntityId(),
							update.getExpectedVersion(), update.getName(), update.getDescription(),
							properties(update.getProperties())))
					.toList();
		List<DiagramViews.EdgeInput> edges = (request.getEdges() == null) ? List.of()
				: request.getEdges()
					.stream()
					.map((edge) -> new DiagramViews.EdgeInput(edge.getId(), edge.getConnectionId(),
							edge.getNewConnectionKey(), edge.getSourceShapeId(), edge.getTargetShapeId(),
							edge.getPoints()
								.stream()
								.map((point) -> new DiagramViews.Point(point.getX(), point.getY()))
								.toList()))
					.toList();
		List<DiagramViews.NewConnectionInput> newConnections = (request.getNewConnections() == null) ? List.of()
				: request.getNewConnections()
					.stream()
					.map((connection) -> new DiagramViews.NewConnectionInput(connection.getKey(),
							connection.getMetatypeId(), connection.getName(), connection.getDescription(),
							properties(connection.getProperties())))
					.toList();
		List<DiagramViews.LineInput> lines = (request.getHierarchyLines() == null) ? List.of()
				: request.getHierarchyLines()
					.stream()
					.map((line) -> new DiagramViews.LineInput(line.getId(), line.getParentShapeId(),
							line.getChildShapeId()))
					.toList();
		List<DiagramViews.ParentChangeInput> parentChanges = (request.getParentChanges() == null) ? List.of()
				: request.getParentChanges()
					.stream()
					.map((change) -> new DiagramViews.ParentChangeInput(change.getEntityId(),
							change.getExpectedVersion(), change.getParentEntityId(), change.getParentNewEntityKey()))
					.toList();
		// the 400 for more decimals comes first, then the 403 of a scoping connection type, then the service
		this.views.checkNumbers(shapes, edges);
		this.visibility.requireScopeAssignment(principal(),
				newConnections.stream().map(DiagramViews.NewConnectionInput::metatypeId).toList());
		return view(this.views.save(principal(), id, expectedVersion(ifMatch), shapes, newEntities, updates, edges,
				newConnections, lines, parentChanges, new AuditEvent.Agent(EntityBatchController.informational(xAgentId),
						EntityBatchController.informational(xAgentRun))));
	}

	@Override
	public ResponseEntity<DiagramConnectionOfferList> findDiagramConnections(UUID id,
			DiagramConnectionQuery request) {
		DiagramViews.Offers offers = this.views.connections(id, request.getEntityIds(), request.getShownEntityIds());
		return ResponseEntity.ok(new DiagramConnectionOfferList(offers.items()
			.stream()
			.map((offer) -> new DiagramConnectionOffer(
					new DiagramEdgeConnection(offer.connectionId(), offer.name(), offer.metatypeId()),
					offer.sourceEntityId(), offer.targetEntityId()))
			.toList(), offers.truncated()));
	}

	@Override
	public ResponseEntity<DiagramEntityPage> searchDiagramEntities(UUID id, String q, UUID metatypeId, Integer page,
			Integer pageSize) {
		return entityPage(this.views.search(id, q, metatypeId, page(page), size(pageSize)));
	}

	private static ResponseEntity<DiagramEntityPage> entityPage(DiagramViews.EntityPage result) {
		return ResponseEntity.ok(new DiagramEntityPage(result.items()
			.stream()
			.map((found) -> new DiagramEntity(found.id(), found.name(),
					new MetatypeSummary().id(found.metatype().id())
						.name(found.metatype().name())
						.abbreviation(found.metatype().abbreviation()),
					found.childCount(), found.parentEntityId()))
			.toList(), new PageMeta(result.page(), result.pageSize(), Math.toIntExact(result.totalElements()),
					result.totalPages())));
	}

	@Override
	public ResponseEntity<DiagramEntityChildList> listDiagramEntityChildren(UUID id, Integer entityId) {
		DiagramViews.Children children = this.views.children(id, entityId);
		return ResponseEntity.ok(new DiagramEntityChildList(children.items()
			.stream()
			.map((child) -> new DiagramEntityChild(
					new DiagramShapeEntity(child.id(), child.name(), child.metatypeId(), child.childCount(),
							child.parentEntityId()),
					child.allowed(),
					(child.compositionConnection() == null) ? null
							: new DiagramChildConnection(new DiagramEdgeConnection(child.compositionConnection().id(),
									child.compositionConnection().name(), child.compositionConnection().metatypeId()),
									child.compositionConnection().allowed())))
			.toList(), children.truncated()));
	}

	@Override
	public ResponseEntity<DiagramEntityDetails> getDiagramEntityDetails(UUID id, Integer entityId) {
		DiagramViews.Details details = this.views.details(principal(), id, entityId);
		return ResponseEntity.ok(new DiagramEntityDetails(details.id(), details.name(), details.description(),
				new MetatypeSummary().id(details.metatypeId())
					.name(details.metatypeName())
					.abbreviation(details.metatypeAbbreviation()),
				details.version(), details.properties().stream().map(DiagramController::value).toList(),
				details.fields()
					.stream()
					.map((field) -> new DiagramEntityField(field.propertyDefId(), field.readable(), field.writable()))
					.toList(),
				details.missingMandatory()));
	}

	@Override
	public ResponseEntity<DiagramEntityPage> searchDiagramReferenceTargets(UUID id, UUID propertyDefId, String q,
			Integer page, Integer pageSize) {
		return entityPage(this.views.referenceTargets(id, propertyDefId, q, page(page), size(pageSize)));
	}

	private static List<io.oea.app.entity.EntityInput.PropertyInput> properties(
			List<io.oea.api.generated.model.PropertyWriteValue> properties) {
		return (properties == null) ? List.of()
				: properties.stream()
					.map((property) -> new io.oea.app.entity.EntityInput.PropertyInput(property.getPropertyDefId(),
							property.getValue()))
					.toList();
	}

	private static PropertyValue value(DiagramViews.Value value) {
		PropertyValue result = new PropertyValue().propertyDefId(value.propertyDefId())
			.propertyName(value.propertyName())
			.dataType(PropertyValue.DataTypeEnum.fromValue(value.dataType()))
			.redacted(value.redacted());
		DiagramViews.Typed typed = value.typed();
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

	private static ResponseEntity<DiagramView> view(DiagramViews.View view) {
		Diagram diagram = diagram(view.diagram());
		return ResponseEntity.ok()
			.eTag(etag(diagram.getVersion()))
			.body(new DiagramView(diagram, ViewpointController.viewpoint(view.viewpoint()),
					view.shapes()
						.stream()
						.map((shape) -> new DiagramShape(shape.id(),
								new DiagramShapeEntity(shape.entityId(), shape.entityName(), shape.metatypeId(),
										shape.childCount(), shape.parentEntityId()),
								shape.parentShapeId(), shape.x(), shape.y(), shape.width(), shape.height(),
								shape.missingMandatory()))
						.toList(),
					view.edges()
						.stream()
						.map((edge) -> new DiagramEdge(edge.id(),
								new DiagramEdgeConnection(edge.connectionId(), edge.connectionName(),
										edge.metatypeId()),
								edge.sourceShapeId(), edge.targetShapeId(),
								edge.points().stream().map((point) -> new DiagramPoint(point.x(), point.y())).toList(),
								edge.missingMandatory()))
						.toList(),
					view.hierarchyLines()
						.stream()
						.map((line) -> new DiagramHierarchyLine(line.id(), line.parentShapeId(), line.childShapeId()))
						.toList(),
					view.askNewParent()));
	}

	private static PageMeta meta(Diagrams.Page result) {
		return new PageMeta(result.page(), result.pageSize(), Math.toIntExact(result.totalElements()),
				result.totalPages());
	}

	private static int page(Integer page) {
		return (page != null) ? page : 0;
	}

	private static int size(Integer pageSize) {
		return (pageSize != null) ? pageSize : 50;
	}

	private static ResponseEntity<Diagram> versioned(Diagram diagram) {
		return ResponseEntity.ok().eTag(etag(diagram.getVersion())).body(diagram);
	}

	private static Diagram diagram(Diagrams.Diagram row) {
		return new Diagram(row.id(), row.name(), row.description(),
				new DiagramViewpoint(row.viewpoint().id(), row.viewpoint().name(),
						ViewpointNotation.fromValue(row.viewpoint().notation())),
				row.version(), principalRef(row.createdBy()), row.createdAt().atOffset(ZoneOffset.UTC),
				row.updatedAt().atOffset(ZoneOffset.UTC));
	}

	private static DeletedDiagram deleted(Diagrams.Diagram row) {
		return new DeletedDiagram(row.id(), row.name(), row.description(),
				new DiagramViewpoint(row.viewpoint().id(), row.viewpoint().name(),
						ViewpointNotation.fromValue(row.viewpoint().notation())),
				row.version(), principalRef(row.createdBy()), row.createdAt().atOffset(ZoneOffset.UTC),
				row.updatedAt().atOffset(ZoneOffset.UTC), row.deletedAt().atOffset(ZoneOffset.UTC),
				principalRef(row.deletedBy()));
	}

	private static PrincipalRef principalRef(Diagrams.Actor actor) {
		return new PrincipalRef(actor.serviceAccount() ? PrincipalRef.PrincipalTypeEnum.SERVICE_ACCOUNT
				: PrincipalRef.PrincipalTypeEnum.PERSON, actor.name()).id(Math.toIntExact(actor.id()));
	}

	private static String etag(Integer version) {
		return "\"" + version + "\"";
	}

	/** The version of a strong {@code If-Match} ETag; {@code null} for none, a weak or a malformed one (never matches). */
	private static Long expectedVersion(String ifMatch) {
		if (ifMatch == null) {
			return null;
		}
		Matcher matcher = STRONG_VERSION.matcher(ifMatch.strip());
		return matcher.matches() ? Long.valueOf(matcher.group(1)) : Long.valueOf(-1);
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
