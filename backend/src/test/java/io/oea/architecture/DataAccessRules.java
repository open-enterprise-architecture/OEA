package io.oea.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * SQL only in core (ADR-051, issue #258): classes of the application layer neither use
 * Spring JDBC nor {@code java.sql}; they read and write through the stores of {@code core}.
 * Parameterized by the root package so the rule can also be checked against fixtures.
 */
final class DataAccessRules {

	private DataAccessRules() {
	}

	static ArchRule noSqlInApplicationLayer(String rootPackage) {
		return noClasses().that()
			.resideInAPackage(rootPackage + ".app..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
			.because("ADR-051: SQL runs only in the stores of core; app orchestrates through them");
	}

}
