package io.oea.architecture.fixtures.wronglayer.infrastructure;

import io.oea.architecture.fixtures.wronglayer.app.AppService;

/** Violation: an infrastructure adapter depends on the application layer. */
public class InfrastructureUsingApp {

	public String run() {
		return AppService.name();
	}

}
