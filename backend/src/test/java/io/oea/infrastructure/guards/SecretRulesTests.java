package io.oea.infrastructure.guards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Boundaries of the minimum secret length (G4): the last valid and the first invalid length.
 */
class SecretRulesTests {

	private final SecretRules rules = SecretRules.load();

	@Test
	void minimumLength() {
		assertThat(this.rules.check("Kq7#mW2!vX9$pL4z")).as("16 characters").isEmpty();
		assertThat(this.rules.check("Kq7#mW2!vX9$pL4")).as("15 characters")
			.contains("is shorter than 16 characters");
	}

}
