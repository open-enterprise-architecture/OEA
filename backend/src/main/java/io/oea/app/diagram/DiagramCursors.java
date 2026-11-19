package io.oea.app.diagram;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cursors to the next page of the diagram list (design D3 of us-074-diagrams): the sort name and ID
 * of the last row of a page with the fingerprint of the query and the number of the page they lead
 * to, as base64url of a compact JSON document. The values only position the next page and reach the
 * database as parameters, so no signature is needed.
 */
final class DiagramCursors {

	/** The longest cursor the contract accepts. */
	static final int MAX_LENGTH = 2000;

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private DiagramCursors() {
	}

	/** The position after a row: its sort name and ID. */
	record Position(String sortName, UUID id) {
	}

	static String fingerprint(List<String> parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String part : parts) {
				String text = (part != null) ? part : "";
				digest.update((text.length() + ":" + text + ";").getBytes(StandardCharsets.UTF_8));
			}
			return ENCODER.encodeToString(digest.digest());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String encode(String fingerprint, int page, Position position) {
		ObjectNode json = JsonMapper.shared().createObjectNode();
		json.put("f", fingerprint);
		json.put("p", page);
		json.put("n", position.sortName());
		json.put("i", position.id().toString());
		return ENCODER.encodeToString(JsonMapper.shared().writeValueAsBytes(json));
	}

	/** The position of a cursor issued for the query with the fingerprint and for {@code page}; empty otherwise. */
	static Optional<Position> decode(String cursor, String fingerprint, int page) {
		if (cursor == null || cursor.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		try {
			JsonNode json = JsonMapper.shared().readTree(Base64.getUrlDecoder().decode(cursor));
			if (json == null || !fingerprint.equals(json.path("f").asString(null)) || json.path("p").asInt(-1) != page
					|| !json.path("n").isString() || !json.path("i").isString()) {
				return Optional.empty();
			}
			return Optional.of(new Position(json.path("n").asString(), UUID.fromString(json.path("i").asString())));
		}
		catch (IllegalArgumentException | JacksonException ex) {
			return Optional.empty();
		}
	}

}
