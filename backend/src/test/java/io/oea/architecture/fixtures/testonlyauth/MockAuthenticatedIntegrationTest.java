package io.oea.architecture.fixtures.testonlyauth;

import io.oea.architecture.fixtures.testonlyauth.securitytest.MockUser;
import org.springframework.boot.test.context.SpringBootTest;

/** Violation: an integration test using mock authentication. */
@SpringBootTest(classes = ProfileConditionalSecurityConfiguration.class)
class MockAuthenticatedIntegrationTest {

	String principal() {
		return MockUser.user();
	}

}
