package io.oea.architecture.fixtures.wronglayer.api;

import io.oea.architecture.fixtures.wronglayer.core.CoreService;

/** Violation: the API layer bypasses the application layer. */
public class ControllerUsingCore {

	public String handle() {
		return new CoreService().compute();
	}

}
