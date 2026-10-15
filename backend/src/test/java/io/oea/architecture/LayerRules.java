package io.oea.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Layer rules of ADR-028: dependencies point inward ({@code api → app → core}).
 * Parameterized by the root package so the rules can also be checked against fixtures.
 */
final class LayerRules {

	private LayerRules() {
	}

	static ArchRule layersPointInward(String rootPackage) {
		return layeredArchitecture().consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer("API")
			.definedBy(rootPackage + ".api..")
			.layer("Application")
			.definedBy(rootPackage + ".app..")
			.layer("Core")
			.definedBy(rootPackage + ".core..")
			.whereLayer("API")
			.mayNotBeAccessedByAnyLayer()
			.whereLayer("Application")
			.mayOnlyBeAccessedByLayers("API")
			.whereLayer("Core")
			.mayOnlyBeAccessedByLayers("Application")
			.because("ADR-028: api knows app, not core; app knows core, not api; core knows nothing above it");
	}

}
