package io.oea.api.metamodel;

import io.oea.api.generated.ViewpointsApi;
import io.oea.api.generated.model.EntityHistoryActor;
import io.oea.api.generated.model.MetatypeSummary;
import io.oea.api.generated.model.ViewpointConfig;
import io.oea.api.generated.model.ViewpointWrite;
import io.oea.app.metamodel.Viewpoints;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** The generic viewpoints of the instance (US-090, US-093; design D3 of us-090-viewpoints). */
@RestController
public class ViewpointController implements ViewpointsApi {

	/** Path of the viewpoint collection. */
	public static final String VIEWPOINTS_PATH = "/api/v1/admin/viewpoints";

	private final Viewpoints viewpoints;

	public ViewpointController(Viewpoints viewpoints) {
		this.viewpoints = viewpoints;
	}

	@Override
	public ResponseEntity<List<ViewpointConfig>> listViewpoints() {
		return ResponseEntity.ok(this.viewpoints.list().stream().map(ViewpointController::viewpoint).toList());
	}

	@Override
	public ResponseEntity<ViewpointConfig> createViewpoint(ViewpointWrite request) {
		ViewpointConfig created = viewpoint(this.viewpoints.create(principal(), input(request)));
		return ResponseEntity.created(URI.create(VIEWPOINTS_PATH + "/" + created.getId()))
			.eTag(etag(created.getVersion()))
			.body(created);
	}

	@Override
	public ResponseEntity<ViewpointConfig> getViewpoint(UUID id) {
		ViewpointConfig viewpoint = viewpoint(this.viewpoints.get(id));
		return ResponseEntity.ok().eTag(etag(viewpoint.getVersion())).body(viewpoint);
	}

	@Override
	public ResponseEntity<ViewpointConfig> updateViewpoint(UUID id, String ifMatch, ViewpointWrite request) {
		ViewpointConfig viewpoint = viewpoint(this.viewpoints.update(principal(), id, input(request),
				MetamodelController.expectedVersion(ifMatch)));
		return ResponseEntity.ok().eTag(etag(viewpoint.getVersion())).body(viewpoint);
	}

	@Override
	public ResponseEntity<Void> deleteViewpoint(UUID id, String ifMatch) {
		this.viewpoints.delete(principal(), id, MetamodelController.expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	private static Viewpoints.ViewpointInput input(ViewpointWrite request) {
		return new Viewpoints.ViewpointInput(request.getName(), request.getDescription(), request.getMetatypeIds(),
				request.getConnectionTypeIds());
	}

	private static ViewpointConfig viewpoint(Viewpoints.Viewpoint row) {
		return new ViewpointConfig().id(row.id())
			.name(row.name())
			.description(row.description())
			.allowedMetatypes(row.metatypes().stream().map(ViewpointController::summary).toList())
			.allowedRelations(row.connectionTypes().stream().map(ViewpointController::summary).toList())
			.version(row.version())
			.createdAt(row.createdAt().atOffset(ZoneOffset.UTC))
			.createdBy(new EntityHistoryActor(row.createdById(), row.createdByName()));
	}

	private static MetatypeSummary summary(Viewpoints.Type type) {
		return new MetatypeSummary().id(type.id()).name(type.name()).abbreviation(type.abbreviation());
	}

	private static String etag(Integer version) {
		return "\"" + version + "\"";
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
