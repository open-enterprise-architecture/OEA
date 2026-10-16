package io.oea.api.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-033 §7: agent headers are stored only as cleaned, limited informational values. */
class AgentHeaderTests {

	@Test
	void controlCharactersAreRemovedAndTheValueIsLimited() {
		assertThat(EntityBatchController.informational("repo\u0007-scanner\n")).isEqualTo("repo-scanner");
		assertThat(EntityBatchController.informational("x".repeat(150))).hasSize(100);
		assertThat(EntityBatchController.informational(" \t ")).isNull();
		assertThat(EntityBatchController.informational(null)).isNull();
	}

}
