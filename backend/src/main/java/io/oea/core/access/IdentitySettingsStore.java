package io.oea.core.access;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The identity columns of {@code system_settings}: password policy, second-factor policy and the
 * setup phase (S17 change 3, ADR-051).
 */
@Repository
public class IdentitySettingsStore {

	/** The stored password policy. */
	public record PasswordSettings(int minLength, boolean requireUppercase, boolean requireLowercase,
			boolean requireDigits, boolean requireSpecialChars, int expiryDays, int historyCount,
			boolean personPasswordLogin) {
	}

	/** The stored second-factor policy. */
	public record SecondFactorSettings(boolean enforced, List<String> exemptRoles) {
	}

	private final JdbcTemplate jdbc;

	public IdentitySettingsStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The stored password policy; empty on an instance without settings. */
	public Optional<PasswordSettings> passwordSettings() {
		return this.jdbc.query("""
				SELECT password_min_length, password_require_uppercase, password_require_lowercase,
				       password_require_digits, password_require_special_chars, password_expiry_days,
				       password_history_count, person_password_login
				FROM system_settings WHERE id = 1""",
				(rs, n) -> new PasswordSettings(rs.getInt(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
						rs.getBoolean(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8)))
			.stream()
			.findFirst();
	}

	/** Replaces the password policy; returns the number of changed rows. */
	public int updatePasswordSettings(PasswordSettings settings, Instant at, Long byPersonId, UUID byAdminId) {
		return this.jdbc.update("""
				UPDATE system_settings SET password_min_length = ?, password_require_uppercase = ?,
				       password_require_lowercase = ?, password_require_digits = ?, password_require_special_chars = ?,
				       password_expiry_days = ?, password_history_count = ?, person_password_login = ?, updated_at = ?,
				       updated_by_person_id = ?, updated_by_admin_id = ?
				WHERE id = 1""", settings.minLength(), settings.requireUppercase(), settings.requireLowercase(),
				settings.requireDigits(), settings.requireSpecialChars(), settings.expiryDays(),
				settings.historyCount(), settings.personPasswordLogin(), Timestamp.from(at), byPersonId, byAdminId);
	}

	/** The stored second-factor policy; empty on an instance without settings. */
	public Optional<SecondFactorSettings> secondFactorSettings() {
		return this.jdbc.query(
				"SELECT second_factor_enforced, second_factor_exempt_roles FROM system_settings WHERE id = 1",
				(rs, n) -> {
					Array array = rs.getArray(2);
					List<String> roles = (array != null) ? Arrays.asList((String[]) array.getArray()) : List.of();
					return new SecondFactorSettings(rs.getBoolean(1), List.copyOf(roles));
				})
			.stream()
			.findFirst();
	}

	/** Replaces the second-factor policy; returns the number of changed rows. */
	public int updateSecondFactorSettings(boolean enforced, List<String> exemptRoles, Instant at, Long byPersonId,
			UUID byAdminId) {
		return this.jdbc.update("""
				UPDATE system_settings SET second_factor_enforced = ?, second_factor_exempt_roles = ?, updated_at = ?,
				       updated_by_person_id = ?, updated_by_admin_id = ?
				WHERE id = 1""", enforced, exemptRoles.toArray(String[]::new), Timestamp.from(at), byPersonId,
				byAdminId);
	}

	/** Whether the setup phase has ended; {@code false} without settings. */
	public boolean setupPhaseEnded() {
		return Boolean.TRUE.equals(this.jdbc.query("SELECT setup_phase_ended_at IS NOT NULL FROM system_settings WHERE id = 1",
				(rs) -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE));
	}

	/** Whether the setup phase has ended, locking the settings row ({@code FOR UPDATE}). */
	public boolean lockSetupPhaseEnded() {
		return Boolean.TRUE.equals(this.jdbc.query(
				"SELECT setup_phase_ended_at IS NOT NULL FROM system_settings WHERE id = 1 FOR UPDATE",
				(rs) -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE));
	}

	/** Ends the setup phase. */
	public void endSetupPhase(Instant at, String action) {
		this.jdbc.update("UPDATE system_settings SET setup_phase_ended_at = ?, setup_phase_ended_by_action = ? WHERE id = 1",
				Timestamp.from(at), action);
	}

}
