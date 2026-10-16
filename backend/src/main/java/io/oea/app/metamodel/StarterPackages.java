package io.oea.app.metamodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The metamodel packages shipped with OEA in {@code classpath:packages/} (ADR-005, REQ-112,
 * design D6): read once at startup, listed with a summary, imported by identifier.
 */
@Service
public class StarterPackages {

	private final JdbcTemplate jdbc;

	private final Map<String, Shipped> packages;

	/** A shipped package: its summary and its content in the package format. */
	public record Shipped(String packageId, String name, String version, String publisher, String description,
			List<String> metatypeNames, int propertyDefinitionCount, byte[] content) {
	}

	/** Summary of a shipped package for the list. */
	public record Summary(Shipped shipped, boolean imported) {
	}

	public StarterPackages(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.packages = read().stream()
			.collect(Collectors.toUnmodifiableMap(Shipped::packageId, Function.identity()));
	}

	/** All shipped packages, for the startup check of their content. */
	public List<Shipped> shipped() {
		return this.packages.values().stream().sorted(Comparator.comparing(Shipped::packageId)).toList();
	}

	@Transactional(readOnly = true)
	public List<Summary> list() {
		Set<String> imported = new HashSet<>(
				this.jdbc.queryForList("SELECT DISTINCT package_id FROM metamodel_package_imports", String.class));
		return shipped().stream().map((shipped) -> new Summary(shipped, imported.contains(shipped.packageId()))).toList();
	}

	/** The shipped package with this identifier; {@link NoSuchElementException} (404) otherwise. */
	public Shipped get(String packageId) {
		Shipped shipped = this.packages.get(packageId);
		if (shipped == null) {
			throw new NoSuchElementException("no starter package " + packageId);
		}
		return shipped;
	}

	private static List<Shipped> read() {
		List<Shipped> shipped = new ArrayList<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:packages/*.json")) {
				byte[] content;
				try (InputStream in = resource.getInputStream()) {
					content = in.readAllBytes();
				}
				JsonNode root = JsonMapper.shared().readTree(content);
				List<String> names = root.path("metatypes").valueStream().map((m) -> m.path("name").asString()).toList();
				shipped.add(new Shipped(root.path("packageId").asString(), root.path("name").asString(),
						root.path("packageVersion").asString(), root.path("publisher").asString(),
						root.path("description").asString(""), names, root.path("propertyDefinitions").size(), content));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("starter packages cannot be read", ex);
		}
		return shipped;
	}

}
