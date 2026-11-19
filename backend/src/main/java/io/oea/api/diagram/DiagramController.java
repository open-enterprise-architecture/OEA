package io.oea.api.diagram;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.DiagramsApi;
import io.oea.api.generated.model.DeletedDiagram;
import io.oea.api.generated.model.DeletedDiagramPage;
import io.oea.api.generated.model.Diagram;
import io.oea.api.generated.model.DiagramCreate;
import io.oea.api.generated.model.DiagramPage;
import io.oea.api.generated.model.DiagramUpdate;
import io.oea.api.generated.model.DiagramViewpoint;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.ViewpointNotation;
import io.oea.app.diagram.Diagrams;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
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

	public DiagramController(Diagrams diagrams) {
		this.diagrams = diagrams;
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
