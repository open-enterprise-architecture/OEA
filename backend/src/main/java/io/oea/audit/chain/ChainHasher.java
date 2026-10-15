package io.oea.audit.chain;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes {@code record_hash} exactly as docs/audit-hash-chain.md specifies:
 * SHA-256 over length-prefixed fields; NULL is encoded as {@code FF FF FF FF}.
 */
public final class ChainHasher {

	public static final int HASH_VERSION = 1;

	private static final byte[] NULL_MARKER = { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };

	private ChainHasher() {
	}

	/** Hash of one link from its header and the value bytes of its fields in order. */
	public static byte[] recordHash(int hashVersion, long chainSeq, byte[] prevHash, ChainTable table,
			List<byte[]> fieldValues) {
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		field(input, ascii(Integer.toString(hashVersion)));
		field(input, ascii(Long.toString(chainSeq)));
		field(input, prevHash);
		field(input, ascii(table.tag()));
		fieldValues.forEach((value) -> field(input, value));
		return sha256(input.toByteArray());
	}

	/** Value bytes of all hashed columns of the current row, in the order of the table. */
	public static List<byte[]> fieldValues(ChainTable table, ResultSet row) throws SQLException {
		List<byte[]> values = new ArrayList<>(table.columns().size());
		for (var column : table.columns().entrySet()) {
			values.add(column.getValue().valueBytes(row, column.getKey()));
		}
		return values;
	}

	static void field(ByteArrayOutputStream out, byte[] value) {
		if (value == null) {
			out.writeBytes(NULL_MARKER);
			return;
		}
		out.writeBytes(ByteBuffer.allocate(4).putInt(value.length).array());
		out.writeBytes(value);
	}

	public static byte[] utf8(String value) {
		return (value != null) ? value.getBytes(StandardCharsets.UTF_8) : null;
	}

	private static byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] sha256(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
