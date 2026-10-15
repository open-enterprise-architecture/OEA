package io.oea.architecture;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Spec platform/startup-safety: No test-only authentication code in the production artifact. */
class TestOnlyAuthenticationArchitectureTests {

	private static final String FIXTURES = "io.oea.architecture.fixtures.testonlyauth";

	private static final JavaClasses MAIN = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("io.oea");

	private static final JavaClasses TESTS = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
		.importPackages("io.oea");

	@Test
	void mainCodeHasNoTestOnlyAuthenticationCode() {
		TestOnlyAuthenticationRules
			.mainCodeDoesNotDependOnTestOnlyLibraries(TestOnlyAuthenticationRules.TEST_ONLY_PACKAGES)
			.check(MAIN);
		TestOnlyAuthenticationRules.mainCodeHasNoStubClassNames().check(MAIN);
		TestOnlyAuthenticationRules.securityBeansAreNotProfileConditional().check(MAIN);
	}

	@Test
	void integrationTestsDoNotUseMockAuthentication() {
		TestOnlyAuthenticationRules
			.integrationTestsDoNotUseMockAuthentication("io.oea.steps", "org.springframework.security.test")
			.check(TESTS);
	}

	@Test
	void rulesDetectFixtureViolations() {
		JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> TestOnlyAuthenticationRules.mainCodeHasNoStubClassNames().check(fixtures))
			.withMessageContaining("FakeJwtIssuer");
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> TestOnlyAuthenticationRules
				.mainCodeDoesNotDependOnTestOnlyLibraries("io.oea.testsupport..")
				.check(fixtures))
			.withMessageContaining("UsesTestSupport");
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> TestOnlyAuthenticationRules.securityBeansAreNotProfileConditional().check(fixtures))
			.withMessageContaining("devOnlyDecoder");
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> TestOnlyAuthenticationRules
				.integrationTestsDoNotUseMockAuthentication("io.oea.steps", FIXTURES + ".securitytest")
				.check(fixtures))
			.withMessageContaining("MockAuthenticatedIntegrationTest");
	}

}
