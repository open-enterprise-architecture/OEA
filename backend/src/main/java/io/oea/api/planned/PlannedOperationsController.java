package io.oea.api.planned;

import io.oea.api.generated.AnalyticsApi;
import io.oea.api.generated.AuditApi;
import io.oea.api.generated.EntitiesApi;
import io.oea.api.generated.EventsApi;
import io.oea.api.generated.I18NApi;
import io.oea.api.generated.PortalApi;
import io.oea.api.generated.ViewsEditorsApi;
import io.oea.api.generated.ViewsNavigationApi;
import java.util.Optional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Maps the generated interfaces that no controller implements yet, so that their planned
 * operations are authenticated and authorized like every other operation and answered with 501
 * (spec platform/api-contract, s2b-ci-and-contract D2). A change that implements the first
 * operation of an interface moves the interface to its own controller.
 */
@RestController
public class PlannedOperationsController implements AnalyticsApi, AuditApi, EntitiesApi, EventsApi, I18NApi,
		PortalApi, ViewsEditorsApi, ViewsNavigationApi {

	/** Every interface declares this default; no example responses are rendered. */
	@Override
	public Optional<NativeWebRequest> getRequest() {
		return Optional.empty();
	}

}
