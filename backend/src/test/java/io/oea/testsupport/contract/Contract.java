package io.oea.testsupport.contract;

import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** The API contract {@code api/openapi.yaml} for tests that compare implementation and contract. */
public final class Contract {

	private static final JsonNode ROOT = YAMLMapper.builder().build().readTree(Path.of("..", "api", "openapi.yaml").toFile());

	private Contract() {
	}

	public static JsonNode root() {
		return ROOT;
	}

}
