package io.oea.architecture.fixtures.wronglayer.app;

import io.oea.architecture.fixtures.wronglayer.api.Controller;

/** Violation: the application layer depends on the API layer. */
public class AppServiceCallingApi {

	public String run() {
		return Controller.path();
	}

}
