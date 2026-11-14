package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The configured maximum secret validity (design D3 of us-142-service-accounts): 1 to 3650 days. */
class ServiceAccountPropertiesTests {

	@ParameterizedTest
	@ValueSource(ints = { 1, 3650 })
	void maximumWithinTheBoundsIsAccepted(int days) {
		assertThat(new ServiceAccountProperties(days).secretMaxValidityDays()).isEqualTo(days);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 3651 })
	void maximumOutsideTheBoundsStopsTheStartup(int days) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ServiceAccountProperties(days))
			.withMessageContaining("oea.service-accounts.secret-max-validity-days");
	}

}
