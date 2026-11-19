package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.metamodel.MetamodelLabelStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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

	private final MetamodelLabelStore store;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelConfigurations configurations;

	public MetamodelLabels(MetamodelLabelStore store, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelConfigurations configurations) {
		this.store = store;
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
		return this.store.elements(PackageImporter.INSTANCE_CONFIGURATION, locale)
			.stream()
			.map((row) -> new Label(new LabelKey(LabelKey.Element.ofColumn(row.entityType()), row.entityId()),
					row.name(), row.packageText(), row.organizationText()))
			.toList();
	}

	/**
	 * The labels of property definitions in a locale: organization, then package, then the name
	 * (retired properties included). Without a supported locale, the names.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, String> propertyLabels(String locale, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return new LinkedHashMap<>();
		}
		String language = (locale != null && LabelKey.LOCALES.contains(locale)) ? locale : "";
		return this.store.propertyLabels(language, ids);
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
		Instant now = this.effects.now();
		int set = 0;
		int removed = 0;
		for (Map.Entry<LabelKey, String> change : changes.entrySet()) {
			LabelKey key = change.getKey();
			if (change.getValue().isEmpty()) {
				removed += this.store.deleteOrganization(key.element().column(), key.id(), locale,
						key.element().field());
			}
			else {
				this.store.upsertOrganization(key.element().column(), key.id(), locale, key.element().field(),
						change.getValue(), now);
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
		this.store.deleteImported(packageId);
		Instant now = this.effects.now();
		int count = 0;
		for (Map.Entry<String, Map<String, String>> bundle : bundles.entrySet()) {
			for (Map.Entry<String, String> label : bundle.getValue().entrySet()) {
				LabelKey key = LabelKey.parse(label.getKey()).orElseThrow();
				this.store.insertImported(key.element().column(), key.id(), bundle.getKey(), key.element().field(),
						label.getValue().strip(), packageId, now);
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
