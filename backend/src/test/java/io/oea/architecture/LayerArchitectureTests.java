package io.oea.architecture;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayerArchitectureTests {

	private static final String FIXTURES = "io.oea.architecture.fixtures.wronglayer";

	@Test
	void productionCodeFollowsLayerRules() {
		JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("io.oea");
		LayerRules.layersPointInward("io.oea").check(classes);
	}

	@Test
	void layerRulesDetectWrongDependencies() {
		JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);
		assertThatExceptionOfType(AssertionError.class)
			.isThrownBy(() -> LayerRules.layersPointInward(FIXTURES).check(fixtures))
			.withMessageContaining("CoreServiceCallingApi")
			.withMessageContaining("AppServiceCallingApi")
			.withMessageContaining("ControllerUsingCore");
	}

}
