package io.oea.audit.chain;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Column types of the chain tables and their value bytes (docs/audit-hash-chain.md). */
public enum ColumnKind {

	TEXT, INTEGER, BOOLEAN, UUID, TIMESTAMP, INET, JSONB, BYTEA;

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
		.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT);

	/** Value bytes of the column in the current row, or {@code null} for SQL NULL. */
	byte[] valueBytes(ResultSet row, String column) throws SQLException {
		if (this == BYTEA) {
			return row.getBytes(column);
		}
		String text = switch (this) {
			case BOOLEAN -> {
				boolean value = row.getBoolean(column);
				yield row.wasNull() ? null : Boolean.toString(value);
			}
			case TIMESTAMP -> {
				OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
				yield (value != null) ? TIMESTAMP_FORMAT.format(value.withOffsetSameInstant(ZoneOffset.UTC)) : null;
			}
			case UUID -> {
				String value = row.getString(column);
				yield (value != null) ? value.toLowerCase(Locale.ROOT) : null;
			}
			// TEXT, INTEGER: text form; INET, JSONB: PostgreSQL output function form
			default -> row.getString(column);
		};
		return (text != null) ? text.getBytes(StandardCharsets.UTF_8) : null;
	}

	static String formatTimestamp(OffsetDateTime value) {
		return TIMESTAMP_FORMAT.format(value.withOffsetSameInstant(ZoneOffset.UTC));
	}

}
