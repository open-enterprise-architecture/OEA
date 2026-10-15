package io.oea.testsupport.audit;

import io.oea.audit.chain.ChainHasher;
import io.oea.audit.chain.ChainTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

/**
 * What an attacker with superuser rights does to the audit chain, for tests of the
 * verification command (REQ-174 AC3, AC5). Works on {@code audit.events} only.
 */
public final class ChainTampering {

	private ChainTampering() {
	}

	public static void appendEvents(Connection app, String prefix, int count) throws SQLException {
		try (Statement statement = app.createStatement()) {
			for (int i = 1; i <= count; i++) {
				statement.executeUpdate("""
						INSERT INTO audit.events (event_type, event_category, severity, outcome, source, actor_type,
						    chain_seq, hash_version, prev_hash, record_hash)
						VALUES ('%s-%d', 'system', 'info', 'success', 'Job', 'system', 0, 0, ''::bytea, ''::bytea)
						""".formatted(prefix, i));
			}
		}
	}

	public static long headSeq(Connection su) throws SQLException {
		try (Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT last_seq FROM audit.chain_head WHERE id = 1")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	public static byte[] recordHash(Connection su, long chainSeq) throws SQLException {
		try (PreparedStatement select = su.prepareStatement("""
				SELECT record_hash FROM audit.events WHERE chain_seq = ?
				UNION ALL SELECT record_hash FROM audit.event_changes WHERE chain_seq = ?
				UNION ALL SELECT record_hash FROM audit.retention_settings WHERE chain_seq = ?""")) {
			for (int i = 1; i <= 3; i++) {
				select.setLong(i, chainSeq);
			}
			try (ResultSet rs = select.executeQuery()) {
				rs.next();
				return rs.getBytes(1);
			}
		}
	}

	/** Writes a log file with one checkpoint line for the link {@code chainSeq}, as the backend logs it. */
	public static Path checkpointLog(Connection su, long chainSeq, Path file) throws SQLException, IOException {
		Files.writeString(file, "INFO other line\nINFO event=audit.checkpoint chainSeq=" + chainSeq + " recordHash="
				+ HexFormat.of().formatHex(recordHash(su, chainSeq)) + " at=2026-10-15T09:00:00.000000Z\n");
		return file;
	}

	/** Deletes all events from {@code fromSeq} on and rewrites the head, as after restoring an older backup. */
	public static void truncateFrom(Connection su, long fromSeq) throws SQLException {
		try (Statement statement = su.createStatement()) {
			statement.executeUpdate("DELETE FROM audit.events WHERE chain_seq >= " + fromSeq);
		}
		try (PreparedStatement head = su
			.prepareStatement("UPDATE audit.chain_head SET last_seq = ?, last_hash = ? WHERE id = 1")) {
			head.setLong(1, fromSeq - 1);
			head.setBytes(2, recordHash(su, fromSeq - 1));
			head.executeUpdate();
		}
	}

	/** Recomputes every event hash from {@code fromSeq} on, so that the chain alone looks intact. */
	public static void recomputeFrom(Connection su, long fromSeq) throws SQLException {
		byte[] prev = recordHash(su, fromSeq - 1);
		try (Statement statement = su.createStatement();
				ResultSet rs = statement
					.executeQuery("SELECT * FROM audit.events WHERE chain_seq >= " + fromSeq + " ORDER BY chain_seq");
				PreparedStatement update = su
					.prepareStatement("UPDATE audit.events SET prev_hash = ?, record_hash = ? WHERE chain_seq = ?")) {
			while (rs.next()) {
				byte[] hash = ChainHasher.recordHash(rs.getInt("hash_version"), rs.getLong("chain_seq"), prev,
						ChainTable.EVENTS, ChainHasher.fieldValues(ChainTable.EVENTS, rs));
				update.setBytes(1, prev);
				update.setBytes(2, hash);
				update.setLong(3, rs.getLong("chain_seq"));
				update.executeUpdate();
				prev = hash;
			}
		}
		try (PreparedStatement head = su.prepareStatement("UPDATE audit.chain_head SET last_hash = ? WHERE id = 1")) {
			head.setBytes(1, prev);
			head.executeUpdate();
		}
	}

}
