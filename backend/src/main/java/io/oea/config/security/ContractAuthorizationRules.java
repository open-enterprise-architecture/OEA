package io.oea.config.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Authorization rules of every operation, read from the packaged contract
 * ({@code x-oea-authorization}, ADR-032 §8, design D3), and the permission catalog.
 */
public final class ContractAuthorizationRules {

	public static final String CONTRACT = "openapi/openapi.yaml";

	static final String API_PREFIX = "/api/v1";

	private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch");

	private final Map<String, Rule> rules;

	private final List<PublicEndpoint> publicEndpoints;

	private final Map<String, String> permissionDescriptions;

	/** Access rule of one operation. */
	public record Rule(String operationId, Access access, List<String> permissions, List<String> anyOfPermissions,
			boolean hasConditions) {

		public enum Access {

			PUBLIC, AUTHENTICATED, PERMISSION

		}

		/** True if a principal holding {@code granted} may call the operation. */
		public boolean allows(Set<String> granted) {
			return switch (this.access) {
				case PUBLIC, AUTHENTICATED -> true;
				case PERMISSION -> !this.permissions.isEmpty() ? granted.containsAll(this.permissions)
						: this.anyOfPermissions.stream().anyMatch(granted::contains);
			};
		}

	}

	/** Method and path pattern (with {@code *} for path parameters) of a public operation. */
	public record PublicEndpoint(String method, String pathPattern) {
	}

	private ContractAuthorizationRules(Map<String, Rule> rules, List<PublicEndpoint> publicEndpoints,
			Map<String, String> permissionDescriptions) {
		this.rules = Map.copyOf(rules);
		this.publicEndpoints = List.copyOf(publicEndpoints);
		this.permissionDescriptions = Map.copyOf(permissionDescriptions);
	}

	public static ContractAuthorizationRules load() {
		try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
			return parse(YAMLMapper.builder().build().readTree(in));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("API contract " + CONTRACT + " cannot be read", ex);
		}
	}

	static ContractAuthorizationRules parse(JsonNode contract) {
		Map<String, Rule> rules = new LinkedHashMap<>();
		List<PublicEndpoint> publicEndpoints = new ArrayList<>();
		for (var path : contract.path("paths").properties()) {
			for (var operation : path.getValue().properties()) {
				if (!METHODS.contains(operation.getKey())) {
					continue;
				}
				JsonNode op = operation.getValue();
				String operationId = op.path("operationId").asString();
				JsonNode declaration = op.path("x-oea-authorization");
				if (operationId.isEmpty() || declaration.isMissingNode()) {
					throw new IllegalStateException("operation " + operation.getKey() + " " + path.getKey()
							+ " lacks operationId or x-oea-authorization (REQ-158)");
				}
				Rule.Access access = Rule.Access.valueOf(declaration.path("access").asString().toUpperCase());
				Rule rule = new Rule(operationId, access, strings(declaration.path("permissions")),
						strings(declaration.path("anyOfPermissions")), declaration.has("conditions"));
				rules.put(operationId, rule);
				if (access == Rule.Access.PUBLIC) {
					publicEndpoints.add(new PublicEndpoint(operation.getKey().toUpperCase(),
							API_PREFIX + path.getKey().replaceAll("\\{[^/]+}", "*")));
				}
			}
		}
		Map<String, String> descriptions = new LinkedHashMap<>();
		contract.path("x-oea-permissions")
			.forEach((p) -> descriptions.put(p.path("id").asString(), p.path("description").asString()));
		return new ContractAuthorizationRules(rules, publicEndpoints, descriptions);
	}

	public Rule rule(String operationId) {
		return this.rules.get(operationId);
	}

	public Set<String> operationIds() {
		return this.rules.keySet();
	}

	public List<PublicEndpoint> publicEndpoints() {
		return this.publicEndpoints;
	}

	public Map<String, String> permissionDescriptions() {
		return this.permissionDescriptions;
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((value) -> values.add(value.asString()));
		return values;
	}

}
