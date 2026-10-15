package io.oea.config.startup;

import java.util.List;

/** Startup refused; each violation names the offending setting, never a secret value. */
public class StartupGuardException extends RuntimeException {

	private final List<String> violations;

	public StartupGuardException(List<String> violations) {
		super("Startup refused, " + violations.size() + " unsafe setting(s):" + System.lineSeparator() + "  - "
				+ String.join(System.lineSeparator() + "  - ", violations));
		this.violations = List.copyOf(violations);
	}

	public List<String> getViolations() {
		return this.violations;
	}

}
