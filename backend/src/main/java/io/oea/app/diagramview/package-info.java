/**
 * The canvas of a diagram (UC-10, US-074; design D3 and D4 of us-074-diagram-canvas): the view with
 * its shapes, the entity search of "Add existing" and the atomic save of the layout with new
 * entities. The package depends on {@code app.diagram}, {@code app.metamodel} and
 * {@code app.entity}; none of them depends on it, so that no package cycle arises (ADR-051).
 */
package io.oea.app.diagramview;
