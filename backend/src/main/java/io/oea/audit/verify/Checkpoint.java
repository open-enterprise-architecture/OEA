package io.oea.audit.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A chain head exported to the application log (docs/audit-hash-chain.md, Checkpoints). The
 * entry is read from the fields of a JSON log line ({@code ecs}, {@code logstash}, or
 * {@code gelf} with {@code _}-prefixed fields) or from the message of a plain text line
 * (design D4 of s3-structured-logging-and-icons).
 */
public record Checkpoint(long chainSeq, byte[] recordHash) {

	private static final Pattern LINE = Pattern
		.compile("event=audit\\.checkpoint chainSeq=(\\d+) recordHash=([0-9a-f]{64})");

	private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

	/** Reads every checkpoint line of a log file; other lines are ignored. */
	public static List<Checkpoint> read(Path file) throws IOException {
		return Files.readAllLines(file, StandardCharsets.UTF_8)
			.stream()
			.map(Checkpoint::parse)
			.flatMap(Optional::stream)
			.toList();
	}

	/** The checkpoint of one log line, if it is one. */
	static Optional<Checkpoint> parse(String line) {
		Optional<Checkpoint> fromFields = fromJson(line);
		if (fromFields.isPresent()) {
			return fromFields;
		}
		Matcher m = LINE.matcher(line);
		return m.find() ? Optional.of(new Checkpoint(Long.parseLong(m.group(1)), HexFormat.of().parseHex(m.group(2))))
				: Optional.empty();
	}

	private static Optional<Checkpoint> fromJson(String line) {
		// log collectors may put a prefix such as the service name in front of the object
		int start = line.indexOf('{');
		if (start < 0) {
			return Optional.empty();
		}
		JsonNode json;
		try {
			json = JsonMapper.shared().readTree(line.substring(start));
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
		if (!json.isObject() || !"audit.checkpoint".equals(field(json, "event").asString(null))) {
			return Optional.empty();
		}
		JsonNode seq = field(json, "chainSeq");
		String hash = field(json, "recordHash").asString("");
		if (!seq.canConvertToLong() && !seq.asString("").matches("\\d+") || !HASH.matcher(hash).matches()) {
			return Optional.empty();
		}
		return Optional.of(new Checkpoint(seq.asLong(), HexFormat.of().parseHex(hash)));
	}

	/** A field by name, or with the {@code _} prefix of GELF additional fields. */
	private static JsonNode field(JsonNode json, String name) {
		return json.has(name) ? json.path(name) : json.path("_" + name);
	}

}
