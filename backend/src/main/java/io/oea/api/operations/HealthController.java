package io.oea.api.operations;

import io.oea.api.generated.OperationsApi;
import io.oea.api.generated.model.HealthStatus;
import io.oea.api.generated.model.HealthStatus.StatusEnum;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health probe (REQ-075). Aggregates the registered health indicators (database,
 * disk space, ...) and exposes only the overall status; component details stay internal.
 * The Actuator health endpoint itself is not exposed.
 */
@RestController
public class HealthController implements OperationsApi {

	private final HealthContributors contributors;

	public HealthController(HealthContributors contributors) {
		this.contributors = contributors;
	}

	@Override
	public ResponseEntity<HealthStatus> getHealth() {
		boolean up = isUp(this.contributors);
		HealthStatus body = new HealthStatus(up ? StatusEnum.UP : StatusEnum.DOWN);
		return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

	/** DOWN as soon as one indicator reports DOWN or OUT_OF_SERVICE; UNKNOWN does not count. */
	private static boolean isUp(HealthContributors contributors) {
		return contributors.stream().allMatch((entry) -> isUp(entry.contributor()));
	}

	private static boolean isUp(HealthContributor contributor) {
		if (contributor instanceof CompositeHealthContributor composite) {
			return isUp((HealthContributors) composite);
		}
		if (contributor instanceof HealthIndicator indicator) {
			Status status = indicator.health(false).getStatus();
			return !Status.DOWN.equals(status) && !Status.OUT_OF_SERVICE.equals(status);
		}
		return true;
	}

}
