/**
 * Deleting an entity with its cascade and the preview of it (REQ-146, US-074; ADR-019; change
 * s17-entity-delete). The restore of a deleted entity lives in {@code app.entity}, so that the
 * batch write can restore the external ID of a deleted entity (#70) without a package cycle.
 */
package io.oea.app.deletion;
