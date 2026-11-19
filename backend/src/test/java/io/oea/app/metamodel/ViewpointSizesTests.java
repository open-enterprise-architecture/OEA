package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.metamodel.ViewpointStore.SizeRow;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The rules of the default sizes (s17-viewpoint-default-sizes D3), without a database. */
class ViewpointSizesTests {

	private static final UUID ELEMENT = UUID.randomUUID();

	private static final UUID CONNECTION = UUID.randomUUID();

	private static final UUID OTHER = UUID.randomUUID();

	@Test
	void theDefaultsAre90By30AndALengthOf90() {
		assertThat(ViewpointSizes.DEFAULT_WIDTH).isEqualTo(90);
		assertThat(ViewpointSizes.DEFAULT_HEIGHT).isEqualTo(30);
		assertThat(ViewpointSizes.DEFAULT_LENGTH).isEqualTo(90);
	}

	@Test
	void noSizesAndValidSizesHaveNoProblem() {
		assertThat(problem(List.of())).isEmpty();
		assertThat(problem(List.of(new SizeRow(ELEMENT, 90, 30, null), new SizeRow(CONNECTION, null, null, 90))))
			.isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "1, 1, true", "2000, 2000, true", "0, 30, false", "90, 0, false", "2001, 30, false", "90, 2001, false" })
	void anElementSizeIsBetween1And2000(int width, int height, boolean valid) {
		assertThat(problem(List.of(new SizeRow(ELEMENT, width, height, null))).isEmpty()).isEqualTo(valid);
	}

	@ParameterizedTest
	@CsvSource({ "1, true", "2000, true", "0, false", "2001, false" })
	void aLengthIsBetween1And2000(int length, boolean valid) {
		assertThat(problem(List.of(new SizeRow(CONNECTION, null, null, length))).isEmpty()).isEqualTo(valid);
	}

	@Test
	void theKindOfSizeMustFitTheKindOfMetatype() {
		assertThat(problem(List.of(new SizeRow(ELEMENT, null, null, 90)))).isPresent();
		assertThat(problem(List.of(new SizeRow(ELEMENT, 90, null, null)))).isPresent();
		assertThat(problem(List.of(new SizeRow(ELEMENT, null, 30, null)))).isPresent();
		assertThat(problem(List.of(new SizeRow(ELEMENT, 90, 30, 90)))).isPresent();
		assertThat(problem(List.of(new SizeRow(CONNECTION, 90, null, null)))).isPresent();
		assertThat(problem(List.of(new SizeRow(CONNECTION, null, 30, 90)))).isPresent();
		assertThat(problem(List.of(new SizeRow(CONNECTION, null, null, null)))).isPresent();
	}

	@Test
	void aSizeNeedsAnAllowedMetatypeAndAtMostOnePerMetatype() {
		assertThat(problem(List.of(new SizeRow(OTHER, 90, 30, null)))).isPresent();
		assertThat(problem(List.of(new SizeRow(ELEMENT, 90, 30, null), new SizeRow(ELEMENT, 91, 31, null))))
			.isPresent();
	}

	private static java.util.Optional<String> problem(List<SizeRow> sizes) {
		return ViewpointSizes.problem(Set.of(ELEMENT), Set.of(CONNECTION), sizes);
	}

}
