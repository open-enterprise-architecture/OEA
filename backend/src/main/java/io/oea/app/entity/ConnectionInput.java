package io.oea.app.entity;

/**
 * A connection to write (us-036-connection-metatypes D3): the fields of an entity of a connection
 * metatype and its source and target, each by internal ID or external ID.
 */
public record ConnectionInput(EntityInput entity, Integer sourceId, String sourceExternalId, Integer targetId,
		String targetExternalId) {
}
