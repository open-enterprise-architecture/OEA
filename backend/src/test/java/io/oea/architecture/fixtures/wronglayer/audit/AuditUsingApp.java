package io.oea.architecture.fixtures.wronglayer.audit;

import io.oea.architecture.fixtures.wronglayer.app.AppService;

/** Violation: the audit layer depends on the application layer. */
public class AuditUsingApp {

	public String run() {
		return AppService.name();
	}

}
