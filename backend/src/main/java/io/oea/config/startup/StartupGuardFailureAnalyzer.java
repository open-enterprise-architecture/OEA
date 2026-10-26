package io.oea.config.startup;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Reports refused startups with one line per offending setting. */
class StartupGuardFailureAnalyzer extends AbstractFailureAnalyzer<StartupGuardException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, StartupGuardException cause) {
		return new FailureAnalysis(cause.getMessage(),
				"Correct the named settings. Development relaxations apply only with the Spring profile 'dev' "
						+ "(ADR-036 §6); see the user manual, https://open-enterprise-architecture.github.io/OEA/setup/installation",
				cause);
	}

}
