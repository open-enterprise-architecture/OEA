package io.oea.testsupport.metamodel;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Metamodel packages in the contract format (ADR-026) with stable identifiers per package. */
public final class TestPackages {

	private TestPackages() {
	}

	/** A package with the given number of metatypes, one enum property and one category. */
	public static ObjectNode pkg(String packageId, int metatypes) {
		ObjectNode root = JsonMapper.shared().createObjectNode();
		root.put("formatVersion", "1.0").put("exportedAt", "2026-10-16T00:00:00Z").put("packageId", packageId)
			.put("packageVersion", "1.0.0").put("publisher", "Test");
		String categoryId = uuid(packageId, "category");
		root.putArray("propertyCategories").addObject().put("id", categoryId).put("name", packageId + " General")
			.put("sortOrder", 0);
		String propertyId = uuid(packageId, "property");
		ObjectNode property = root.putArray("propertyDefinitions").addObject();
		property.put("id", propertyId).put("name", packageId + " Status").put("dataType", "enum");
		property.putObject("category").put("id", categoryId).put("name", packageId + " General").put("sortOrder", 0);
		ArrayNode values = property.putArray("enumValues");
		List<String> names = List.of("planned", "active");
		for (int i = 0; i < names.size(); i++) {
			values.addObject().put("id", uuid(packageId, "value" + i)).put("value", names.get(i)).put("sortOrder", i);
		}
		ArrayNode types = root.putArray("metatypes");
		for (int i = 1; i <= metatypes; i++) {
			ObjectNode type = types.addObject();
			type.put("id", uuid(packageId, "type" + i)).put("name", packageId + " Type " + i).put("abbreviation", "T" + i)
				.put("sortOrder", i);
			ObjectNode mapping = type.putArray("propertyMappings").addObject();
			mapping.put("id", uuid(packageId, "mapping" + i)).put("necessity", "optional").put("validFrom", "2026-01-01")
				.put("sortOrder", 0);
			mapping.putObject("propertyDefinition").put("id", propertyId).put("name", packageId + " Status")
				.put("dataType", "enum");
		}
		return root;
	}

	static String uuid(String packageId, String element) {
		return UUID.nameUUIDFromBytes((packageId + "/" + element).getBytes()).toString();
	}

}
