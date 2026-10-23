package io.oea.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/**
 * Problem responses are declared at the operation (design D2 of s2b-backend-hardening): the
 * shared handler never decides by the request path.
 */
class ApiExceptionHandlerTests {

	@Test
	void problemResponsesDoNotDependOnTheRequestPath() {
		noClasses().that()
			.belongToAnyOf(ApiExceptionHandler.class)
			.should()
			.callMethod(HttpServletRequest.class, "getRequestURI")
			.orShould()
			.callMethod(HttpServletRequest.class, "getServletPath")
			.orShould()
			.callMethod(HttpServletRequest.class, "getRequestURL")
			.because("the operation declares its contract-violation response (@ContractViolationResponse)")
			.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("io.oea.api"));
	}

}
