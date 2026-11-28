/**
 * The data lineage query (UC-08, US-065; design D4 of us-065-lineage-model-and-query): the walk
 * over the data flows from a data object or a system. The package depends on
 * {@code app.metamodel}; none of them depends on it, so that no package cycle arises (ADR-051).
 */
package io.oea.app.lineage;
