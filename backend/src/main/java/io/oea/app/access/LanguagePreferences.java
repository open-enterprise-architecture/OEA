package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.core.access.PersonStore;
import java.util.Set;
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

	private final PersonStore persons;

	public LanguagePreferences(PersonStore persons) {
		this.persons = persons;
	}

	/** The chosen language of the principal, or {@code null}. */
	@Transactional(readOnly = true)
	public String language(OeaPrincipal principal) {
		if (principal.personId() == null) {
			return null;
		}
		return this.persons.language(principal.personId());
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
		if (principal.personId() == null || this.persons.updateLanguage(principal.personId(), language) == 0) {
			throw new PermissionDeniedException();
		}
	}

}
