package io.oea.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.contract.Contract;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Every operation that can receive a request violating the contract documents the status it
 * answers it with: 400 {@code validation-failed}, or the status its handler declares with
 * {@link ContractViolationResponse}.
 */
class ContractViolationStatusTests {

	private static final Set<String> CHECKED_PARAMETER_LOCATIONS = Set.of("path", "query");

	private static final Set<String> CONSTRAINTS = Set.of("format", "enum", "minimum", "maximum", "exclusiveMinimum",
			"exclusiveMaximum", "minLength", "maxLength", "pattern", "minItems", "maxItems");

	@Test
	void everyOperationDocumentsItsContractViolationStatus() {
		assertThat(undocumented(Contract.root(), handlerStatuses())).isEmpty();
	}

	@Test
	void anOperationWithABodyNeedsTheStatus() {
		JsonNode contract = contract("""
				paths:
				  /things:
				    post:
				      operationId: createThing
				      requestBody: { content: { application/json: { schema: { type: object } } } }
				      responses:
				        '201': { description: created }
				        '422': { description: rule violated }
				""");
		assertThat(undocumented(contract, Map.of("createThing", 400))).containsExactly("createThing: 400");
		assertThat(undocumented(contract, Map.of("createThing", 422))).isEmpty();
	}

	@Test
	void aDocumentedStatusIsAccepted() {
		JsonNode contract = contract("""
				paths:
				  /things:
				    post:
				      operationId: createThing
				      requestBody: { content: { application/json: { schema: { type: object } } } }
				      responses:
				        '201': { description: created }
				        '400': { description: invalid }
				""");
		assertThat(undocumented(contract, Map.of("createThing", 400))).isEmpty();
		assertThat(undocumented(contract, Map.of("createThing", 422))).containsExactly("createThing: 422");
	}

	@Test
	void aConstrainedParameterNeedsTheStatusAndAnUnconstrainedOneDoesNot() {
		JsonNode contract = contract("""
				components:
				  parameters:
				    Page: { name: page, in: query, schema: { type: integer, minimum: 0 } }
				paths:
				  /things/{id}:
				    get:
				      operationId: getThing
				      parameters:
				        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
				      responses: { '200': { description: ok } }
				  /things:
				    get:
				      operationId: listThings
				      parameters:
				        - $ref: '#/components/parameters/Page'
				      responses: { '200': { description: ok } }
				  /search:
				    get:
				      operationId: search
				      parameters:
				        - { name: q, in: query, schema: { type: string } }
				        - { name: X-Agent, in: header, required: true, schema: { type: string, maxLength: 100 } }
				      responses: { '200': { description: ok } }
				""");
		assertThat(undocumented(contract, Map.of("getThing", 400, "listThings", 400, "search", 400)))
			.containsExactly("getThing: 400", "listThings: 400");
	}

	@Test
	void anOperationWithoutHandlerIsNotChecked() {
		JsonNode contract = contract("""
				paths:
				  /token:
				    post:
				      operationId: issueToken
				      requestBody: { content: { application/json: { schema: { type: object } } } }
				      responses: { '200': { description: ok } }
				""");
		assertThat(undocumented(contract, Map.of())).isEmpty();
	}

	/** The operations whose contract violation status is missing from their responses. */
	static List<String> undocumented(JsonNode contract, Map<String, Integer> statuses) {
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, JsonNode> path : contract.path("paths").properties()) {
			for (Map.Entry<String, JsonNode> entry : path.getValue().properties()) {
				JsonNode operation = entry.getValue();
				Integer status = statuses.get(operation.path("operationId").asString(""));
				if (status != null && receivesInput(contract, operation)
						&& !operation.path("responses").has(String.valueOf(status))) {
					missing.add(operation.path("operationId").asString() + ": " + status);
				}
			}
		}
		return missing.stream().sorted().toList();
	}

	private static boolean receivesInput(JsonNode contract, JsonNode operation) {
		if (operation.has("requestBody")) {
			return true;
		}
		for (JsonNode parameter : operation.path("parameters")) {
			JsonNode resolved = resolve(contract, parameter);
			if (CHECKED_PARAMETER_LOCATIONS.contains(resolved.path("in").asString())
					&& (resolved.path("required").asBoolean(false) || constrained(resolved.path("schema")))) {
				return true;
			}
		}
		return false;
	}

	private static boolean constrained(JsonNode schema) {
		String type = schema.path("type").asString("string");
		return !"string".equals(type) || CONSTRAINTS.stream().anyMatch(schema::has)
				|| (schema.has("items") && constrained(schema.path("items")));
	}

	private static JsonNode resolve(JsonNode contract, JsonNode node) {
		String ref = node.path("$ref").asString("");
		return ref.startsWith("#/") ? contract.at(ref.substring(1)) : node;
	}

	/** The contract violation status of every operation served by a controller, by operation ID. */
	private static Map<String, Integer> handlerStatuses() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		Map<String, Integer> statuses = new TreeMap<>();
		Set<String> controllers = new TreeSet<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents("io.oea.api")) {
			Class<?> controller = load(candidate.getBeanClassName());
			controllers.add(controller.getSimpleName());
			for (Class<?> api : controller.getInterfaces()) {
				if (!api.getPackageName().equals("io.oea.api.generated")) {
					continue;
				}
				for (Method declared : api.getMethods()) {
					if (Modifier.isStatic(declared.getModifiers())
							|| !AnnotatedElementUtils.hasAnnotation(declared, RequestMapping.class)) {
						continue;
					}
					Method handler = method(controller, declared);
					ContractViolationResponse response = handler.getAnnotation(ContractViolationResponse.class);
					statuses.put(declared.getName(), (response != null) ? response.status() : 400);
				}
			}
		}
		assertThat(controllers).as("controllers found").isNotEmpty();
		return statuses;
	}

	private static Method method(Class<?> controller, Method declared) {
		try {
			return controller.getMethod(declared.getName(), declared.getParameterTypes());
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Class<?> load(String name) {
		try {
			return Class.forName(name);
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode contract(String yaml) {
		return YAMLMapper.builder().build().readTree(yaml);
	}

}
