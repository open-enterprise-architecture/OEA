package io.oea.architecture.fixtures.wronglayer.core;

import io.oea.architecture.fixtures.wronglayer.api.Controller;

/** Violation: the domain layer depends on the API layer. */
public class CoreServiceCallingApi {

	public String run() {
		return Controller.path();
	}

}
