package io.oea.audit.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A chain head exported to the application log (docs/audit-hash-chain.md, Checkpoints). */
public record Checkpoint(long chainSeq, byte[] recordHash) {

	private static final Pattern LINE = Pattern
		.compile("event=audit\\.checkpoint chainSeq=(\\d+) recordHash=([0-9a-f]{64})");

	/** Reads every checkpoint line of a log file; other lines are ignored. */
	public static List<Checkpoint> read(Path file) throws IOException {
		return Files.readAllLines(file, StandardCharsets.UTF_8)
			.stream()
			.map(LINE::matcher)
			.filter(Matcher::find)
			.map((m) -> new Checkpoint(Long.parseLong(m.group(1)), HexFormat.of().parseHex(m.group(2))))
			.toList();
	}

}
