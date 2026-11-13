package io.oea.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;

/**
 * No cycles between packages (ADR-028, amendment 2026-11-13; design D6 of
 * s11-review-hardening): every package below the root package is a slice of its own, so a
 * cycle between two features of a layer is reported as well as one between layers.
 */
final class CycleRules {

	private CycleRules() {
	}

	static ArchRule packagesFreeOfCycles(String rootPackage) {
		return slices().matching(rootPackage + ".(**)")
			.should()
			.beFreeOfCycles()
			.because("ADR-028 (amended 2026-11-13): packages form no cycles");
	}

}
