package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Storage names of new organization properties (design D2 of us-032-metatype-editing). */
class StorageNamesTests {

	private static final Set<String> NONE = Set.of();

	@Test
	void specialCharactersBecomeUnderscores() {
		assertThat(StorageNames.forNewProperty("Trust Level (Zone)", NONE::contains)).isEqualTo("trust_level_zone");
		assertThat(StorageNames.forNewProperty("costCenter", NONE::contains)).isEqualTo("cost_center");
		assertThat(StorageNames.forNewProperty("Größe", NONE::contains)).isEqualTo("gr_e");
	}

	@Test
	void aNameWithoutLeadingLetterGetsAPrefix() {
		assertThat(StorageNames.forNewProperty("3rd Party", NONE::contains)).isEqualTo("p_3rd_party");
		assertThat(StorageNames.forNewProperty("!!!", NONE::contains)).isEqualTo("p");
	}

	@Test
	void reservedAndTakenNamesGetASuffix() {
		assertThat(StorageNames.forNewProperty("Name", NONE::contains)).isEqualTo("name_2");
		assertThat(StorageNames.forNewProperty("Owner", Set.of("owner", "owner_2")::contains)).isEqualTo("owner_3");
	}

	@Test
	void theNameIsCutTo63Characters() {
		String name = "a".repeat(80);
		assertThat(StorageNames.forNewProperty(name, NONE::contains)).hasSize(63);
		assertThat(StorageNames.forNewProperty(name, Set.of("a".repeat(63))::contains))
			.isEqualTo("a".repeat(61) + "_2");
	}

}
