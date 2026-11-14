package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Translated labels of metamodel content per locale (change us-140-dynamic-labels, ADR-025):
 * organization labels over package labels over the name in the corporate language (ADR-049),
 * the bundle with its content hash (design D3) and the maintenance by administrators (design D4).
 */
@Service
public class MetamodelLabels {

	/** Maximum number of keys in one change. */
	static final int MAXIMUM_KEYS = 5000;

	private static final String ELEMENTS = """
			WITH elements (entity_type, entity_id, name) AS (
			    SELECT 'metatype', id, name FROM metatypes WHERE config_id = ?
			    UNION ALL SELECT 'property_category', id, name FROM property_categories
			    UNION ALL SELECT 'property_def', id, name FROM property_definitions
			    UNION ALL SELECT 'enum_value', id, value FROM property_enum_values
			)
			SELECT e.entity_type, e.entity_id, e.name, p.value AS package_text, o.value AS organization_text
			FROM elements e
			LEFT JOIN i18n_entries p ON p.entity_type = e.entity_type AND p.entity_id = e.entity_id
			     AND p.locale = ? AND p.scope = 'imported'
			LEFT JOIN i18n_entries o ON o.entity_type = e.entity_type AND o.entity_id = e.entity_id
			     AND o.locale = ? AND o.scope = 'organization'
			ORDER BY e.entity_type, lower(e.name), e.entity_id""";

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelConfigurations configurations;

	public MetamodelLabels(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelConfigurations configurations) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
		this.configurations = configurations;
	}

	/** A label of an element in a locale. */
	public record Label(LabelKey key, String name, String packageText, String organizationText) {

		/** The text that applies: organization, then package, then the name. */
		public String text() {
			if (this.organizationText != null) {
				return this.organizationText;
			}
			return (this.packageText != null) ? this.packageText : this.name;
		}

	}

	/** The bundle of a locale with the weak ETag of its content. */
	public record Bundle(Map<String, String> labels, String etag) {
	}

	/** A locale outside {@link LabelKey#LOCALES} (400). */
	public static class UnsupportedLocaleException extends RuntimeException {

		UnsupportedLocaleException(String locale) {
			super("Unsupported locale " + locale);
		}

	}

	@Transactional(readOnly = true)
	public List<Label> list(String locale) {
		requireLocale(locale);
		return this.jdbc.query(ELEMENTS,
				(rs, row) -> new Label(
						new LabelKey(LabelKey.Element.ofColumn(rs.getString("entity_type")),
								rs.getObject("entity_id", UUID.class)),
						rs.getString("name"), rs.getString("package_text"), rs.getString("organization_text")),
				PackageImporter.INSTANCE_CONFIGURATION, locale, locale);
	}

	/**
	 * The labels of property definitions in a locale: organization, then package, then the name
	 * (retired properties included). Without a supported locale, the names.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, String> propertyLabels(String locale, Collection<UUID> ids) {
		Map<UUID, String> labels = new LinkedHashMap<>();
		if (ids.isEmpty()) {
			return labels;
		}
		String language = (locale != null && LabelKey.LOCALES.contains(locale)) ? locale : "";
		this.jdbc.query("""
				SELECT d.id, coalesce(o.value, p.value, d.name)
				FROM property_definitions d
				LEFT JOIN i18n_entries p ON p.entity_type = 'property_def' AND p.entity_id = d.id
				     AND p.locale = ? AND p.scope = 'imported'
				LEFT JOIN i18n_entries o ON o.entity_type = 'property_def' AND o.entity_id = d.id
				     AND o.locale = ? AND o.scope = 'organization'
				WHERE d.id = ANY(?)""", (rs) -> {
			labels.put(rs.getObject(1, UUID.class), rs.getString(2));
		}, language, language, ids.toArray(UUID[]::new));
		return labels;
	}

	@Transactional(readOnly = true)
	public Bundle bundle(String locale) {
		Map<String, String> labels = new TreeMap<>();
		for (Label label : list(locale)) {
			labels.put(label.key().toString(), label.text());
		}
		return new Bundle(labels, etag(labels));
	}

	/**
	 * Sets or removes organization labels of a locale; a blank text or {@code null} removes.
	 *
	 * @throws MetamodelChangeException with {@code INVALID} naming the unknown keys (nothing stored)
	 */
	@Transactional
	public List<Label> update(OeaPrincipal principal, String locale, Map<String, String> labels) {
		requireLocale(locale);
		if (labels.size() > MAXIMUM_KEYS) {
			throw MetamodelChangeException.invalid("labels", "More than " + MAXIMUM_KEYS + " labels.");
		}
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		Set<String> known = new HashSet<>();
		list(locale).forEach((label) -> known.add(label.key().toString()));
		List<String> invalid = new ArrayList<>();
		Map<LabelKey, String> changes = new LinkedHashMap<>();
		labels.forEach((key, text) -> {
			String stripped = (text != null) ? text.strip() : "";
			if (!known.contains(key) || stripped.length() > LabelKey.MAXIMUM_LENGTH) {
				invalid.add(key);
			}
			else {
				changes.put(LabelKey.parse(key).orElseThrow(), stripped);
			}
		});
		if (!invalid.isEmpty()) {
			throw MetamodelChangeException.invalid(invalid.stream().sorted().toList(), "Unknown label keys.");
		}
		Timestamp now = Timestamp.from(this.effects.now());
		int set = 0;
		int removed = 0;
		for (Map.Entry<LabelKey, String> change : changes.entrySet()) {
			LabelKey key = change.getKey();
			if (change.getValue().isEmpty()) {
				removed += this.jdbc.update("""
						DELETE FROM i18n_entries WHERE entity_type = ? AND entity_id = ? AND locale = ? AND field = ?
						    AND scope = 'organization'""", key.element().column(), key.id(), locale,
						key.element().field());
			}
			else {
				this.jdbc.update("""
						INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, updated_at)
						VALUES (?, ?, ?, ?, ?, 'organization', ?)
						ON CONFLICT (entity_type, entity_id, locale, field, scope)
						DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at""",
						key.element().column(), key.id(), locale, key.element().field(), change.getValue(), now);
				set++;
			}
		}
		this.audit.changed("metamodel.labels-changed", principal, "metamodel-configuration",
				PackageImporter.INSTANCE_CONFIGURATION, Map.of("locale", locale, "labelsSet", set, "labelsRemoved",
						removed));
		return list(locale);
	}

	/** Replaces the package labels of a package; organization labels stay (design D5). */
	int replacePackageLabels(String packageId, Map<String, Map<String, String>> bundles) {
		this.jdbc.update("DELETE FROM i18n_entries WHERE scope = 'imported' AND package_id = ?", packageId);
		Timestamp now = Timestamp.from(this.effects.now());
		int count = 0;
		for (Map.Entry<String, Map<String, String>> bundle : bundles.entrySet()) {
			for (Map.Entry<String, String> label : bundle.getValue().entrySet()) {
				LabelKey key = LabelKey.parse(label.getKey()).orElseThrow();
				this.jdbc.update("""
						INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, package_id, updated_at)
						VALUES (?, ?, ?, ?, ?, 'imported', ?, ?)""", key.element().column(), key.id(), bundle.getKey(),
						key.element().field(), label.getValue().strip(), packageId, now);
				count++;
			}
		}
		return count;
	}

	private static void requireLocale(String locale) {
		if (!LabelKey.LOCALES.contains(locale)) {
			throw new UnsupportedLocaleException(locale);
		}
	}

	private static String etag(Map<String, String> labels) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Map.Entry<String, String> label : labels.entrySet()) {
				digest.update(label.getKey().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
				digest.update(label.getValue().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return "W/\"" + HexFormat.of().formatHex(digest.digest(), 0, 8) + "\"";
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
