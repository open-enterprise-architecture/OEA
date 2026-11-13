package io.oea.architecture;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Cycle rule of ADR-028, amendment 2026-11-13 (ARCH-2 of the architecture review 2026-11). */
class CycleArchitectureTests {

	private static final String FIXTURES = "io.oea.architecture.fixtures.cycle";

	@Test
	void productionCodeIsFreeOfPackageCycles() {
		JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("io.oea");
		CycleRules.packagesFreeOfCycles("io.oea").check(classes);
	}

	@Test
	void cycleRuleDetectsACycleBetweenTwoPackages() {
		JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> CycleRules.packagesFreeOfCycles(FIXTURES).check(fixtures))
			.withMessageContaining("FirstService")
			.withMessageContaining("SecondService");
	}

}
