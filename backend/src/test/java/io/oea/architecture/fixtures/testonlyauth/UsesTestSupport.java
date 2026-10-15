package io.oea.architecture.fixtures.testonlyauth;

import io.oea.testsupport.containers.OeaKeycloak;

/** Violation: main code depending on the test support packages. */
public class UsesTestSupport {

	public String realm() {
		return OeaKeycloak.issuer();
	}

}
