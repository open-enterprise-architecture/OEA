package io.oea.audit.verify;

import io.oea.audit.chain.ChainHasher;
import io.oea.audit.chain.ChainTable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Walks the chain over all audit tables in {@code chain_seq} order, recomputes every hash
 * and checks continuity; optionally compares the chain with exported checkpoints. Reports
 * the first broken link (REQ-174 AC3) and a head behind or diverging from a checkpoint
 * (REQ-174 AC5).
 */
public class ChainVerifier {

	private static final HexFormat HEX = HexFormat.of();

	public VerificationResult verify(Connection connection, List<Checkpoint> checkpoints) throws SQLException {
		connection.setAutoCommit(false);
		connection.setReadOnly(true);
		Map<Long, Checkpoint> wanted = new HashMap<>();
		checkpoints.forEach((checkpoint) -> wanted.put(checkpoint.chainSeq(), checkpoint));
		List<String> problems = new ArrayList<>();
		long links = 0;
		Link previous = null;
		try (LinkCursor cursor = new LinkCursor(connection)) {
			Link link;
			while ((link = cursor.next()) != null && problems.isEmpty()) {
				links++;
				checkLink(previous, link, problems);
				Checkpoint checkpoint = wanted.remove(link.chainSeq());
				if (problems.isEmpty() && checkpoint != null && !Arrays.equals(checkpoint.recordHash(), link.recordHash())) {
					problems.add("chain diverges from checkpoint at chain_seq " + link.chainSeq() + ": checkpoint "
							+ HEX.formatHex(checkpoint.recordHash()) + ", stored " + HEX.formatHex(link.recordHash()));
				}
				previous = link;
			}
		}
		if (problems.isEmpty()) {
			checkHead(connection, previous, problems);
		}
		long headSeq = (previous != null) ? previous.chainSeq() : 0;
		if (problems.isEmpty()) {
			wanted.values()
				.stream()
				.filter((checkpoint) -> checkpoint.chainSeq() > headSeq)
				.mapToLong(Checkpoint::chainSeq)
				.max()
				.ifPresent((seq) -> problems.add("database chain head " + headSeq + " is behind checkpoint chain_seq "
						+ seq + " (records deleted or an older backup restored)"));
		}
		connection.rollback();
		return new VerificationResult(links, headSeq, (previous != null) ? HEX.formatHex(previous.recordHash()) : "",
				problems);
	}

	private static void checkLink(Link previous, Link link, List<String> problems) {
		long expectedSeq = (previous != null) ? previous.chainSeq() + 1 : 1;
		byte[] expectedPrev = (previous != null) ? previous.recordHash() : new byte[32];
		if (link.chainSeq() != expectedSeq) {
			problems.add("first broken link: chain_seq " + expectedSeq + " is missing (next stored link is "
					+ link.chainSeq() + " in " + link.table().qualifiedName() + ")");
		}
		else if (!Arrays.equals(link.prevHash(), expectedPrev)) {
			problems.add("first broken link: chain_seq " + link.chainSeq() + " in " + link.table().qualifiedName()
					+ " does not reference the previous link: expected prev_hash " + HEX.formatHex(expectedPrev)
					+ ", stored " + HEX.formatHex(link.prevHash()));
		}
		else if (!Arrays.equals(link.recomputedHash(), link.recordHash())) {
			problems.add("first broken link: chain_seq " + link.chainSeq() + " in " + link.table().qualifiedName()
					+ ": expected record_hash " + HEX.formatHex(link.recomputedHash()) + ", stored "
					+ HEX.formatHex(link.recordHash()));
		}
	}

	private static void checkHead(Connection connection, Link last, List<String> problems) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet head = statement.executeQuery("SELECT last_seq, last_hash FROM audit.chain_head WHERE id = 1")) {
			if (!head.next()) {
				problems.add("audit.chain_head is missing");
				return;
			}
			long lastSeq = (last != null) ? last.chainSeq() : 0;
			if (head.getLong(1) != lastSeq) {
				problems.add("first broken link: chain_seq " + (lastSeq + 1) + " is missing (audit.chain_head records "
						+ head.getLong(1) + " links, the tables end at " + lastSeq + ")");
			}
			else if (last != null && !Arrays.equals(head.getBytes(2), last.recordHash())) {
				problems.add("audit.chain_head hash differs from the record_hash of chain_seq " + lastSeq);
			}
		}
	}

	/** One stored link with its recomputed hash. */
	record Link(ChainTable table, long chainSeq, byte[] prevHash, byte[] recordHash, byte[] recomputedHash) {

	}

	/** Merges the three chain tables, each read in chain_seq order, into one stream. */
	static final class LinkCursor implements AutoCloseable {

		private final List<Statement> statements = new ArrayList<>();

		private final Map<ChainTable, ResultSet> results = new HashMap<>();

		private final Map<ChainTable, Link> heads = new HashMap<>();

		LinkCursor(Connection connection) throws SQLException {
			for (ChainTable table : ChainTable.values()) {
				Statement statement = connection.createStatement();
				statement.setFetchSize(500);
				this.statements.add(statement);
				ResultSet rows = statement.executeQuery("SELECT * FROM " + table.qualifiedName() + " ORDER BY chain_seq");
				this.results.put(table, rows);
				advance(table);
			}
		}

		Link next() throws SQLException {
			Link smallest = null;
			for (Link candidate : this.heads.values()) {
				if (candidate != null && (smallest == null || candidate.chainSeq() < smallest.chainSeq())) {
					smallest = candidate;
				}
			}
			if (smallest != null) {
				advance(smallest.table());
			}
			return smallest;
		}

		private void advance(ChainTable table) throws SQLException {
			ResultSet row = this.results.get(table);
			if (!row.next()) {
				this.heads.put(table, null);
				return;
			}
			int hashVersion = row.getInt("hash_version");
			long chainSeq = row.getLong("chain_seq");
			byte[] prevHash = row.getBytes("prev_hash");
			byte[] recomputed = ChainHasher.recordHash(hashVersion, chainSeq, prevHash, table,
					ChainHasher.fieldValues(table, row));
			this.heads.put(table, new Link(table, chainSeq, prevHash, row.getBytes("record_hash"), recomputed));
		}

		@Override
		public void close() throws SQLException {
			for (Statement statement : this.statements) {
				statement.close();
			}
		}

	}

}
