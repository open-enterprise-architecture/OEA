package io.oea.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** REQ-027 AC1 to AC5: generated passwords (design D5 of us-027-password-administration). */
class PasswordGeneratorTests {

	private final PasswordGenerator generator = new PasswordGenerator();

	@Test
	void generatedPasswordsSatisfyThePolicyAndDiffer() {
		PasswordPolicy policy = new PasswordPolicy(12, true, true, true, true, 0, 0);
		Set<String> passwords = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String password = this.generator.generate(policy);
			assertThat(policy.violations(password)).as(password).isEmpty();
			assertThat(password).hasSize(16).matches("[\\x21-\\x7E]+");
			assertThat(password.chars().anyMatch(PasswordPolicy::isSpecial)).isTrue();
			passwords.add(password);
		}
		assertThat(passwords).hasSize(1000);
	}

	@Test
	void theLengthFollowsALongerMinimum() {
		assertThat(this.generator.generate(new PasswordPolicy(40, false, false, false, false, 0, 0))).hasSize(40);
		assertThat(this.generator.generate(PasswordPolicy.DEFAULT)).hasSize(16);
	}

	@Test
	void anUnsatisfiablePolicyGivesNoPassword() {
		assertThatExceptionOfType(PasswordGenerator.PolicyUnsatisfiableException.class)
			.isThrownBy(() -> this.generator.generate(new PasswordPolicy(3, true, true, true, true, 0, 0)));
	}

}
