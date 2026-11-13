package io.oea.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Layer rules of ADR-028 with its amendment of 2026-11-13 (design D6 of s11-review-hardening):
 * every class of the root package belongs to a layer, and dependencies point inward. The root
 * package itself (the application class) may use every layer. Parameterized by the root package
 * so the rules can also be checked against fixtures.
 */
final class LayerRules {

	private LayerRules() {
	}

	static ArchRule layersPointInward(String rootPackage) {
		return layeredArchitecture().consideringOnlyDependenciesInAnyPackage(rootPackage + "..")
			.withOptionalLayers(true)
			.ensureAllClassesAreContainedInArchitecture()
			.layer("Root")
			.definedBy(rootPackage)
			.layer("API")
			.definedBy(rootPackage + ".api..")
			.layer("Config")
			.definedBy(rootPackage + ".config..")
			.layer("Application")
			.definedBy(rootPackage + ".app..")
			.layer("Audit")
			.definedBy(rootPackage + ".audit..")
			.layer("Infrastructure")
			.definedBy(rootPackage + ".infrastructure..")
			.layer("Core")
			.definedBy(rootPackage + ".core..")
			.layer("Integration")
			.definedBy(rootPackage + ".integration..")
			.whereLayer("Root")
			.mayNotBeAccessedByAnyLayer()
			.whereLayer("API")
			.mayOnlyBeAccessedByLayers("Root")
			.whereLayer("Config")
			.mayOnlyBeAccessedByLayers("Root", "API")
			.whereLayer("Application")
			.mayOnlyBeAccessedByLayers("Root", "API", "Config")
			.whereLayer("Audit")
			.mayOnlyBeAccessedByLayers("Root", "API", "Config", "Application")
			.whereLayer("Infrastructure")
			.mayOnlyBeAccessedByLayers("Root", "API", "Config", "Application", "Audit")
			.whereLayer("Core")
			.mayOnlyBeAccessedByLayers("Root", "Application")
			.whereLayer("Integration")
			.mayOnlyBeAccessedByLayers("Root")
			.because("ADR-028 (amended 2026-11-13): api → config → app → audit → infrastructure, and app → core; "
					+ "core and infrastructure know nothing above them");
	}

}
