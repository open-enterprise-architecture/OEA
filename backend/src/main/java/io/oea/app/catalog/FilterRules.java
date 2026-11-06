package io.oea.app.catalog;

import io.oea.core.catalog.CatalogQueryStore.Condition;
import io.oea.core.catalog.CatalogQueryStore.Operator;
import io.oea.core.catalog.CatalogQueryStore.ValueType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules of a filter (design D2 of us-051-catalog-filters, catalog BR-12): the operators per
 * filter type and the values each operator takes. Shared by the query parameter {@code filter}
 * and the expressions of saved filters.
 */
final class FilterRules {

	/** At most this many filters apply to a query and make up a saved filter. */
	static final int MAX_FILTERS = 20;

	/** At most this many characters has a filter in its text form, as the query parameter allows. */
	static final int MAX_FILTER_LENGTH = 2000;

	private static final Map<ValueType, Set<Operator>> OPERATORS = Map.of(ValueType.TEXT,
			EnumSet.of(Operator.EQ, Operator.NEQ, Operator.CONTAINS, Operator.STARTS_WITH, Operator.IN,
					Operator.NOT_IN, Operator.IS_NULL, Operator.IS_NOT_NULL),
			ValueType.ENUM,
			EnumSet.of(Operator.EQ, Operator.NEQ, Operator.IN, Operator.NOT_IN, Operator.IS_NULL,
					Operator.IS_NOT_NULL),
			ValueType.BOOLEAN, EnumSet.of(Operator.EQ, Operator.NEQ, Operator.IS_NULL, Operator.IS_NOT_NULL),
			ValueType.NUMBER,
			EnumSet.of(Operator.EQ, Operator.NEQ, Operator.GT, Operator.LT, Operator.GTE, Operator.LTE,
					Operator.IS_NULL, Operator.IS_NOT_NULL),
			ValueType.DATE, EnumSet.of(Operator.EQ, Operator.NEQ, Operator.GT, Operator.LT, Operator.GTE,
					Operator.LTE, Operator.IS_NULL, Operator.IS_NOT_NULL));

	private FilterRules() {
	}

	/** Which part of a filter is invalid. */
	enum Part {

		ATTRIBUTE_PATH("attributePath"), OPERATOR("operator"), VALUE("value");

		private final String field;

		Part(String field) {
			this.field = field;
		}

		String field() {
			return this.field;
		}

	}

	/** A rejected filter with the invalid part and a message. */
	static final class InvalidFilterException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final Part part;

		InvalidFilterException(Part part, String message) {
			super(message);
			this.part = part;
		}

		Part part() {
			return this.part;
		}

	}

	/** A filter in its text form: column key, operator key and values; not checked yet. */
	record Expression(String key, String operator, List<String> values) {
	}

	/**
	 * Parses {@code {key}:{operator}[:{value}]}: everything after the second colon is the value, a
	 * comma-separated list for {@code in} and {@code notIn}; {@code null} if there is no operator.
	 */
	static Expression parse(String filter) {
		int first = filter.indexOf(':');
		if (first <= 0) {
			return null;
		}
		String key = filter.substring(0, first);
		String rest = filter.substring(first + 1);
		int second = rest.indexOf(':');
		String operator = (second < 0) ? rest : rest.substring(0, second);
		String value = (second < 0) ? null : rest.substring(second + 1);
		List<String> values;
		if (value == null) {
			values = List.of();
		}
		else if ("in".equals(operator) || "notIn".equals(operator)) {
			values = Arrays.stream(value.split(",")).map(String::strip).filter((item) -> !item.isEmpty()).toList();
		}
		else if (value.isEmpty() && ("isNull".equals(operator) || "isNotNull".equals(operator))) {
			values = List.of();
		}
		else {
			values = List.of(value);
		}
		return new Expression(key, operator, values);
	}

	/** The operator of a key such as {@code startsWith}; {@code null} if there is none. */
	static Operator operator(String key) {
		if (key == null) {
			return null;
		}
		for (Operator operator : Operator.values()) {
			if (key(operator).equals(key)) {
				return operator;
			}
		}
		return null;
	}

	/** The key of an operator in the contract, such as {@code startsWith}. */
	static String key(Operator operator) {
		String[] words = operator.name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder key = new StringBuilder(words[0]);
		for (int i = 1; i < words.length; i++) {
			key.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
		}
		return key.toString();
	}

	/**
	 * The condition of a filter on the column: the operator must be one of the column's type and
	 * the values must fit it (design D2 of us-051-catalog-filters).
	 */
	static Condition condition(CatalogColumns.Resolved column, String operatorKey, List<String> values) {
		ValueType type = column.type();
		Operator operator = operator(operatorKey);
		if (operator == null || !OPERATORS.get(type).contains(operator)) {
			throw new InvalidFilterException(Part.OPERATOR, "The operator does not apply to the column.");
		}
		boolean empty = operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL;
		boolean list = operator == Operator.IN || operator == Operator.NOT_IN;
		if (empty ? !values.isEmpty() : list ? values.isEmpty() || values.size() > 100 : values.size() != 1) {
			throw new InvalidFilterException(Part.VALUE, empty ? "The operator takes no value."
					: list ? "The operator takes 1 to 100 values." : "The operator takes one value.");
		}
		List<Object> typed = new ArrayList<>(values.size());
		for (String value : values) {
			typed.add(value(column, type, value));
		}
		return new Condition(column.column(), type, operator, typed);
	}

	private static Object value(CatalogColumns.Resolved column, ValueType type, String value) {
		if (value == null || value.isEmpty() || value.length() > 1000) {
			throw new InvalidFilterException(Part.VALUE, "A value has 1 to 1000 characters.");
		}
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidFilterException(Part.VALUE, "A value has no control characters.");
		}
		try {
			return switch (type) {
				case TEXT -> value;
				case ENUM -> {
					if (!column.result().enumValues().contains(value)) {
						throw new InvalidFilterException(Part.VALUE, "The value is not a value of the enumeration.");
					}
					yield value;
				}
				case BOOLEAN -> {
					if (!"true".equals(value) && !"false".equals(value)) {
						throw new InvalidFilterException(Part.VALUE, "The value is true or false.");
					}
					yield Boolean.valueOf(value);
				}
				case NUMBER -> Long.valueOf(value);
				case DATE -> {
					LocalDate date = LocalDate.parse(value);
					if (date.getYear() < 1 || date.getYear() > 9999) {
						throw new InvalidFilterException(Part.VALUE, "The year is between 1 and 9999.");
					}
					yield date;
				}
			};
		}
		catch (NumberFormatException ex) {
			throw new InvalidFilterException(Part.VALUE, "The value is not a whole number.");
		}
		catch (DateTimeParseException ex) {
			throw new InvalidFilterException(Part.VALUE, "The value is not a date (yyyy-MM-dd).");
		}
	}

}
