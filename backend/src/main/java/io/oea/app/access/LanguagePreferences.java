package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The language of the user interface chosen by a person (design D6 of us-140-dynamic-labels);
 * {@code null} means the language of the browser. Not audited: the setting is not security-relevant.
 */
@Service
public class LanguagePreferences {

	/** Languages a person may choose. */
	public static final Set<String> LANGUAGES = Set.of("de", "en", "fr", "it");

	private final JdbcTemplate jdbc;

	public LanguagePreferences(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The chosen language of the principal, or {@code null}. */
	@Transactional(readOnly = true)
	public String language(OeaPrincipal principal) {
		if (principal.personId() == null) {
			return null;
		}
		List<String> languages = this.jdbc.queryForList("SELECT ui_language FROM persons WHERE id = ?", String.class,
				principal.personId());
		return languages.isEmpty() ? null : languages.get(0);
	}

	/**
	 * Sets or clears the language of the calling person.
	 *
	 * @throws PermissionDeniedException for the system admin account and for service accounts
	 */
	@Transactional
	public void update(OeaPrincipal principal, String language) {
		if (language != null && !LANGUAGES.contains(language)) {
			throw new IllegalArgumentException("unsupported language " + language);
		}
		if (principal.personId() == null || this.jdbc.update(
				"UPDATE persons SET ui_language = ? WHERE id = ? AND person_type = 'human'", language,
				principal.personId()) == 0) {
			throw new PermissionDeniedException();
		}
	}

}
