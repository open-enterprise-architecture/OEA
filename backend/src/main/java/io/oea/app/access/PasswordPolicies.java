package io.oea.app.access;

import io.oea.app.login.PersonPasswordProperties;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.service.PasswordGenerator;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.PasswordPolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password policy of the instance (US-027, REQ-028; design D1 to D3 of
 * us-027-password-administration): the stored policy in {@code system_settings}, the operator
 * floors, the check of every new password including the history, and the switch of password login
 * of persons (US-011, realigned).
 */
@Service
public class PasswordPolicies {

	private final JdbcTemplate jdbc;

	private final PasswordHasher hasher;

	private final PasswordGenerator generator;

	private final PasswordPolicyFloors floors;

	private final PersonPasswordProperties personPasswords;

	private final AuditWriter audit;

	private final Clock clock;

	public PasswordPolicies(JdbcTemplate jdbc, PasswordHasher hasher, PasswordGenerator generator,
			PasswordPolicyFloors floors, PersonPasswordProperties personPasswords, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.hasher = hasher;
		this.generator = generator;
		this.floors = floors;
		this.personPasswords = personPasswords;
		this.audit = audit;
		this.clock = clock;
	}

	/** The values administrators maintain. */
	public record Settings(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigits,
			boolean requireSpecialChars, int expiryDays, int historyCount, boolean personPasswordLogin) {

		static final Settings DEFAULT = new Settings(PasswordHasher.MINIMUM_LENGTH, false, false, false, false, 0, 0,
				true);

		Map<String, Object> asMap() {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("minLength", this.minLength);
			values.put("requireUppercase", this.requireUppercase);
			values.put("requireLowercase", this.requireLowercase);
			values.put("requireDigits", this.requireDigits);
			values.put("requireSpecialChars", this.requireSpecialChars);
			values.put("expiryDays", this.expiryDays);
			values.put("historyCount", this.historyCount);
			values.put("personPasswordLogin", this.personPasswordLogin);
			return values;
		}

	}

	/** Stored values, operator floors and the operator permission of password login of persons. */
	public record View(Settings settings, PasswordPolicyFloors floors, boolean personPasswordLoginPermitted) {
	}

	public View view() {
		return new View(stored(), this.floors, this.personPasswords.enabled());
	}

	/** The effective policy: the stricter of floor and stored value for every rule (D1). */
	public PasswordPolicy effective() {
		Settings stored = stored();
		int ceiling = this.floors.maxExpiryDays();
		int expiry = (ceiling == 0) ? stored.expiryDays()
				: (stored.expiryDays() == 0) ? ceiling : Math.min(stored.expiryDays(), ceiling);
		return new PasswordPolicy(Math.max(stored.minLength(), this.floors.minLength()),
				stored.requireUppercase() || this.floors.requireUppercase(),
				stored.requireLowercase() || this.floors.requireLowercase(),
				stored.requireDigits() || this.floors.requireDigits(),
				stored.requireSpecialChars() || this.floors.requireSpecialChars(), expiry,
				Math.max(stored.historyCount(), this.floors.minHistoryCount()));
	}

	/** Password login of persons is possible while the operator permits it and the switch is on (D2). */
	public boolean personPasswordLoginPossible() {
		return this.personPasswords.enabled() && stored().personPasswordLogin();
	}

	/**
	 * Checks a new password against the effective policy, including the history of its owner.
	 * @param personId the owning person, or {@code null}
	 * @param systemAdminAccountId the owning system admin account, or {@code null}
	 * @throws PasswordPolicyViolationException naming every unmet rule
	 */
	public void check(String password, Long personId, UUID systemAdminAccountId) {
		PasswordPolicy policy = effective();
		List<String> unmet = new ArrayList<>(policy.violations(password));
		if (unmet.isEmpty() && policy.historyCount() > 0 && inHistory(password, personId, systemAdminAccountId,
				policy.historyCount())) {
			unmet.add(PasswordPolicy.HISTORY_COUNT);
		}
		if (!unmet.isEmpty()) {
			throw new PasswordPolicyViolationException(unmet);
		}
	}

	/**
	 * A generated password that satisfies the effective policy (REQ-027; design D5). It is checked
	 * against the rules as a safety net; a fresh random password cannot match the history.
	 * @throws PasswordPolicyUnsatisfiableException if no password can satisfy the policy
	 */
	public String generate() {
		PasswordPolicy policy = effective();
		String password;
		try {
			password = this.generator.generate(policy);
		}
		catch (PasswordGenerator.PolicyUnsatisfiableException ex) {
			throw new PasswordPolicyUnsatisfiableException(ex.getMessage());
		}
		List<String> unmet = policy.violations(password);
		if (!unmet.isEmpty()) {
			throw new IllegalStateException("generated password violates the policy: " + unmet);
		}
		return password;
	}

	/**
	 * Replaces the stored policy (D2).
	 * @throws InvalidAccessRequestException naming every field below a floor or inconsistent
	 */
	@Transactional
	public View update(OeaPrincipal principal, Settings requested) {
		Settings old = stored();
		List<String> invalid = new ArrayList<>();
		if (requested.minLength() < this.floors.minLength() || requested.minLength() > PasswordHasher.MAXIMUM_LENGTH
				|| requested.minLength() < requiredTypes(requested)) {
			invalid.add("minLength");
		}
		if (this.floors.requireUppercase() && !requested.requireUppercase()) {
			invalid.add("requireUppercase");
		}
		if (this.floors.requireLowercase() && !requested.requireLowercase()) {
			invalid.add("requireLowercase");
		}
		if (this.floors.requireDigits() && !requested.requireDigits()) {
			invalid.add("requireDigits");
		}
		if (this.floors.requireSpecialChars() && !requested.requireSpecialChars()) {
			invalid.add("requireSpecialChars");
		}
		int ceiling = this.floors.maxExpiryDays();
		if (requested.expiryDays() < 0 || requested.expiryDays() > 3650
				|| (ceiling > 0 && (requested.expiryDays() == 0 || requested.expiryDays() > ceiling))) {
			invalid.add("expiryDays");
		}
		if (requested.historyCount() < this.floors.minHistoryCount() || requested.historyCount() > 24) {
			invalid.add("historyCount");
		}
		// switching it on again needs the permission of the operator (owner decision 2026-10-29)
		if (requested.personPasswordLogin() && !old.personPasswordLogin() && !this.personPasswords.enabled()) {
			invalid.add("personPasswordLogin");
		}
		if (!invalid.isEmpty()) {
			throw new InvalidAccessRequestException(invalid,
					"The password policy is below an operator floor or inconsistent: " + String.join(", ", invalid) + ".");
		}
		int updated = this.jdbc.update("""
				UPDATE system_settings SET password_min_length = ?, password_require_uppercase = ?,
				       password_require_lowercase = ?, password_require_digits = ?, password_require_special_chars = ?,
				       password_expiry_days = ?, password_history_count = ?, person_password_login = ?, updated_at = ?,
				       updated_by_person_id = ?, updated_by_admin_id = ?
				WHERE id = 1""", requested.minLength(), requested.requireUppercase(), requested.requireLowercase(),
				requested.requireDigits(), requested.requireSpecialChars(), requested.expiryDays(),
				requested.historyCount(), requested.personPasswordLogin(), Timestamp.from(this.clock.instant()),
				principal.personId(), principal.systemAdminAccountId());
		if (updated != 1) {
			throw new IllegalStateException("the instance has no settings yet");
		}
		this.audit.append(new AuditEvent("settings.password-policy-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"settings", "password-policy", Map.of("old", old.asMap(), "new", requested.asMap())));
		return view();
	}

	private static int requiredTypes(Settings settings) {
		return new PasswordPolicy(settings.minLength(), settings.requireUppercase(), settings.requireLowercase(),
				settings.requireDigits(), settings.requireSpecialChars(), 0, 0)
			.requiredTypes();
	}

	private Settings stored() {
		List<Settings> rows = this.jdbc.query("""
				SELECT password_min_length, password_require_uppercase, password_require_lowercase,
				       password_require_digits, password_require_special_chars, password_expiry_days,
				       password_history_count, person_password_login
				FROM system_settings WHERE id = 1""",
				(rs, n) -> new Settings(rs.getInt(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
						rs.getBoolean(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8)));
		return rows.isEmpty() ? Settings.DEFAULT : rows.getFirst();
	}

	/** The current password and the last {@code count - 1} revoked passwords of the owner (D3). */
	private boolean inHistory(String password, Long personId, UUID systemAdminAccountId, int count) {
		List<String> hashes = this.jdbc.queryForList("""
				SELECT pc.password_hash FROM local_credentials c JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE c.type = 'password' AND (c.person_id = ? OR c.system_admin_account_id = ?)
				ORDER BY c.status = 'active' DESC, c.revoked_at DESC NULLS LAST, c.created_at DESC
				LIMIT ?""", String.class, personId, systemAdminAccountId, count);
		return hashes.stream().anyMatch((hash) -> this.hasher.matches(password, hash));
	}

}
