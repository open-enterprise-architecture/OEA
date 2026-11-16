package io.oea.architecture;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

/**
 * SQL only in core (ADR-051, ARCH-4 of the architecture review 2026-11). The violations of
 * 2026-11-16 are frozen in {@code src/test/resources/archunit_store}: a new violation fails, a
 * removed one leaves the store (issue #259 tracks the rest).
 */
class DataAccessArchitectureTests {

	private static final String FIXTURES = "io.oea.architecture.fixtures.sqlinapp";

	@Test
	void applicationLayerAddsNoSql() {
		JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("io.oea");
		FreezingArchRule.freeze(DataAccessRules.noSqlInApplicationLayer("io.oea")).check(classes);
	}

	@Test
	void ruleDetectsSqlInTheApplicationLayer() {
		JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> DataAccessRules.noSqlInApplicationLayer(FIXTURES).check(fixtures))
			.withMessageContaining("ServiceWithSql")
			.withMessageContaining("ServiceWithResultSet")
			.withMessageNotContaining("StoreWithSql");
	}

}
