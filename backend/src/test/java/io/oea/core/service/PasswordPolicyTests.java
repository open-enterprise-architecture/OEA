package io.oea.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** REQ-028 AC1, AC4 to AC6: the rules of the password policy (design D3 of us-027-password-administration). */
class PasswordPolicyTests {

	private static final PasswordPolicy ALL = new PasswordPolicy(16, true, true, true, true, 0, 0);

	@Test
	void theDefaultPolicyAcceptsTwelveLowercaseLetters() {
		assertThat(PasswordPolicy.DEFAULT.violations("abcdefghijkl")).isEmpty();
		assertThat(PasswordPolicy.DEFAULT.violations("abcdefghijk")).containsExactly("minLength");
		assertThat(PasswordPolicy.DEFAULT.violations("a".repeat(129))).containsExactly("maxLength");
		assertThat(PasswordPolicy.DEFAULT.violations(null)).containsExactly("minLength");
	}

	@Test
	void everyUnmetRuleIsNamed() {
		assertThat(ALL.violations("abcdefghijklmn")).containsExactly("minLength", "requireUppercase", "requireDigits",
				"requireSpecialChars");
		assertThat(ALL.violations("Abcdefghijklmn1!")).isEmpty();
		assertThat(ALL.violations("ABCDEFGHIJKLMN1!")).containsExactly("requireLowercase");
	}

	@Test
	void everyPrintableAsciiSpecialCharacterCounts() {
		for (char c = 0x21; c <= 0x7E; c++) {
			boolean special = !Character.isLetterOrDigit(c);
			assertThat(ALL.violations("Abcdefghijklmn1" + c)).as("character %s", c)
				.hasSize(special ? 0 : 1);
		}
		assertThat(ALL.violations("Abcdefghijklmn1 ")).as("space").containsExactly("requireSpecialChars");
		assertThat(ALL.violations("Abcdefghijklmn1é")).as("non-ASCII").containsExactly("requireSpecialChars");
	}

	@Test
	void aPolicyIsSatisfiableWhileTheLengthCoversTheRequiredTypes() {
		assertThat(ALL.requiredTypes()).isEqualTo(4);
		assertThat(ALL.satisfiable()).isTrue();
		assertThat(new PasswordPolicy(3, true, true, true, true, 0, 0).satisfiable()).isFalse();
		assertThat(new PasswordPolicy(129, false, false, false, false, 0, 0).satisfiable()).isFalse();
	}

}
