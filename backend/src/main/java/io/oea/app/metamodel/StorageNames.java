package io.oea.app.metamodel;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Storage names of property definitions ({@code sql_column_name}, ADR-034 DM-6): derived once from
 * the name and never changed (design D2 of us-032-metatype-editing).
 */
public final class StorageNames {

	static final int MAXIMUM_LENGTH = 63;

	static final Pattern SQL_COLUMN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

	/** Columns of every reporting view (ADR-034 DM-6). */
	static final Set<String> RESERVED_COLUMNS = Set.of("id", "urn", "metatype_id", "name", "description",
			"is_logical", "created_at", "updated_at");

	private StorageNames() {
	}

	/** Stable SQL column name derived from a property name, as packages use it. */
	public static String derive(String name) {
		String column = name.strip()
			.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		return column.isEmpty() ? "_" : column;
	}

	/** Whether the column name is valid and not reserved. */
	static boolean usable(String column) {
		return SQL_COLUMN.matcher(column).matches() && !RESERVED_COLUMNS.contains(column);
	}

	/**
	 * A valid storage name for a new organization property: the derived name, prefixed with
	 * {@code p_} unless it starts with a letter, cut to 63 characters, and suffixed with
	 * {@code _2}, {@code _3}, … while it is reserved or taken.
	 */
	static String forNewProperty(String name, Predicate<String> taken) {
		String base = derive(name);
		if (base.charAt(0) < 'a' || base.charAt(0) > 'z') {
			base = "p_" + base.replaceAll("^_+", "");
		}
		base = cut(base, MAXIMUM_LENGTH).replaceAll("_+$", "");
		String candidate = base;
		for (int suffix = 2; !usable(candidate) || taken.test(candidate); suffix++) {
			String tail = "_" + suffix;
			candidate = cut(base, MAXIMUM_LENGTH - tail.length()) + tail;
		}
		return candidate;
	}

	private static String cut(String value, int length) {
		return (value.length() > length) ? value.substring(0, length) : value;
	}

}
