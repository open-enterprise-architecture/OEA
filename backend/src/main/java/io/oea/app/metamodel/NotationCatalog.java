package io.oea.app.metamodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The element catalog of the viewpoint notations (US-091, REQ-091; design D2 of
 * us-091-viewpoint-notations): ArchiMate 3.2, the component and class elements of UML, the core
 * elements of BPMN 2.0 and the generic elements, read once at startup from
 * {@code classpath:notations/catalog.json}. The resource is shipped data, not instance data.
 */
@Component
public class NotationCatalog {

	/** The notation without elements of its own; its elements fit every notation. */
	public static final String GENERIC = "generic";

	/** Element shape of a metatype without a mapping. */
	public static final String GENERIC_BOX = "generic:Box";

	/** Relationship shape of a connection type without a mapping. */
	public static final String GENERIC_ARROW = "generic:Arrow";

	private static final String RESOURCE = "notations/catalog.json";

	private static final Map<String, String> PREFIXES = Map.of("archimate3", "archimate:", "uml", "uml:", "bpmn2",
			"bpmn:", GENERIC, "generic:");

	private final Map<String, Element> elements;

	/** An element or relationship shape; {@code notation} is the API value such as {@code archimate3}. */
	public record Element(String key, String notation, String label, boolean relationship, int defaultWidth,
			int defaultHeight) {
	}

	public NotationCatalog() {
		this.elements = read();
	}

	/** All shapes, ordered by notation and key. */
	public List<Element> list() {
		return this.elements.values()
			.stream()
			.sorted(Comparator.comparing(Element::notation).thenComparing(Element::key))
			.toList();
	}

	/** The shape with the key, if the catalog has it. */
	public Optional<Element> element(String key) {
		return Optional.ofNullable(this.elements.get(key));
	}

	/** Whether the key belongs to the notation or is a generic shape, which fits every notation. */
	public boolean fits(String notation, String key) {
		return key.startsWith("generic:") || (PREFIXES.containsKey(notation) && key.startsWith(PREFIXES.get(notation)));
	}

	private static Map<String, Element> read() {
		try (InputStream stream = NotationCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing resource " + RESOURCE);
			}
			Map<String, Element> elements = new LinkedHashMap<>();
			List<JsonNode> entries = new ArrayList<>();
			JsonMapper.shared().readTree(stream).forEach(entries::add);
			for (JsonNode entry : entries) {
				Element element = new Element(entry.path("key").asString(), entry.path("notation").asString(),
						entry.path("label").asString(), "relationship".equals(entry.path("kind").asString()),
						entry.path("defaultWidth").asInt(), entry.path("defaultHeight").asInt());
				if (elements.put(element.key(), element) != null) {
					throw new IllegalStateException("Duplicate catalog key " + element.key());
				}
			}
			return Map.copyOf(elements);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
