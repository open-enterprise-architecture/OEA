package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The element catalog of the notations (US-091 AC1; design D2 of us-091-viewpoint-notations). */
class NotationCatalogTests {

	private static final List<String> ARCHIMATE_ELEMENTS = List.of("BusinessActor", "BusinessRole",
			"BusinessCollaboration", "BusinessInterface", "BusinessProcess", "BusinessFunction", "BusinessInteraction",
			"BusinessEvent", "BusinessService", "BusinessObject", "Contract", "Representation", "Product",
			"ApplicationComponent", "ApplicationCollaboration", "ApplicationInterface", "ApplicationFunction",
			"ApplicationInteraction", "ApplicationProcess", "ApplicationEvent", "ApplicationService", "DataObject",
			"Node", "Device", "SystemSoftware", "TechnologyCollaboration", "TechnologyInterface", "Path",
			"CommunicationNetwork", "TechnologyFunction", "TechnologyProcess", "TechnologyInteraction",
			"TechnologyEvent", "TechnologyService", "Artifact", "Equipment", "Facility", "DistributionNetwork",
			"Material", "Stakeholder", "Driver", "Assessment", "Goal", "Outcome", "Principle", "Requirement",
			"Constraint", "Meaning", "Value", "Resource", "Capability", "ValueStream", "CourseOfAction", "WorkPackage",
			"Deliverable", "ImplementationEvent", "Plateau", "Gap", "Location", "Grouping");

	private static final List<String> ARCHIMATE_RELATIONSHIPS = List.of("Composition", "Aggregation", "Assignment",
			"Realization", "Serving", "Access", "Influence", "Triggering", "Flow", "Specialization", "Association");

	private final NotationCatalog catalog = new NotationCatalog();

	@Test
	void everyArchimate32ElementAndRelationshipIsPresent() {
		for (String name : ARCHIMATE_ELEMENTS) {
			NotationCatalog.Element element = this.catalog.element("archimate:" + name).orElseThrow();
			assertThat(element.relationship()).as(name).isFalse();
			assertThat(element.notation()).isEqualTo("archimate3");
		}
		for (String name : ARCHIMATE_RELATIONSHIPS) {
			assertThat(this.catalog.element("archimate:" + name).orElseThrow().relationship()).as(name).isTrue();
		}
	}

	@Test
	void theCountsPerNotation() {
		Map<String, Long> counts = this.catalog.list()
			.stream()
			.collect(Collectors.groupingBy(NotationCatalog.Element::notation, Collectors.counting()));
		assertThat(counts).containsEntry("archimate3", (long) ARCHIMATE_ELEMENTS.size() + ARCHIMATE_RELATIONSHIPS.size())
			.containsEntry("uml", 12L)
			.containsEntry("bpmn2", 13L)
			.containsEntry("generic", 2L);
		assertThat(ARCHIMATE_ELEMENTS).hasSize(60);
	}

	@Test
	void umlAndBpmnCoreElements() {
		for (String name : List.of("Component", "Interface", "Class", "Package", "Node", "Artifact", "Actor", "UseCase")) {
			assertThat(this.catalog.element("uml:" + name).orElseThrow().relationship()).as(name).isFalse();
		}
		for (String name : List.of("Association", "Dependency", "Realization", "Generalization")) {
			assertThat(this.catalog.element("uml:" + name).orElseThrow().relationship()).as(name).isTrue();
		}
		for (String name : List.of("Task", "SubProcess", "StartEvent", "EndEvent", "IntermediateEvent", "Gateway",
				"DataObject", "DataStore", "Pool", "Lane")) {
			assertThat(this.catalog.element("bpmn:" + name).orElseThrow().relationship()).as(name).isFalse();
		}
		for (String name : List.of("SequenceFlow", "MessageFlow", "Association")) {
			assertThat(this.catalog.element("bpmn:" + name).orElseThrow().relationship()).as(name).isTrue();
		}
		assertThat(this.catalog.element("generic:Box").orElseThrow().relationship()).isFalse();
		assertThat(this.catalog.element("generic:Arrow").orElseThrow().relationship()).isTrue();
	}

	@Test
	void keysAreUniqueAndAtMost100CharactersAndSizesAreWithinTheContract() {
		Set<String> keys = this.catalog.list().stream().map(NotationCatalog.Element::key).collect(Collectors.toSet());
		assertThat(keys).hasSameSizeAs(this.catalog.list());
		for (NotationCatalog.Element element : this.catalog.list()) {
			assertThat(element.key().length()).as(element.key()).isBetween(1, 100);
			assertThat(element.defaultWidth()).as(element.key()).isBetween(1, 2000);
			assertThat(element.defaultHeight()).as(element.key()).isBetween(1, 2000);
			assertThat(element.label()).isNotBlank();
		}
	}

	@ParameterizedTest
	@CsvSource({ "archimate3, archimate:Node, true", "archimate3, generic:Box, true", "archimate3, generic:Arrow, true",
			"archimate3, uml:Node, false", "archimate3, bpmn:Task, false", "uml, uml:Class, true",
			"uml, archimate:Node, false", "bpmn2, bpmn:Task, true", "bpmn2, uml:Class, false",
			"generic, generic:Box, true", "generic, archimate:Node, false", "generic, bpmn:Task, false" })
	void anElementFitsItsOwnNotationAndTheGenericOnes(String notation, String key, boolean fits) {
		assertThat(this.catalog.fits(notation, key)).isEqualTo(fits);
	}

	@Test
	void anUnknownKeyIsNotInTheCatalog() {
		assertThat(this.catalog.element("archimate:Unknown")).isEmpty();
		assertThat(this.catalog.element("archimate:Node ")).isEmpty();
	}

}
