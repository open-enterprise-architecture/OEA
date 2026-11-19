package io.oea.core.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL on {@code system_admin_accounts} (S17 change 3, ADR-051). */
@Repository
public class SystemAdminAccountStore {

	/** The account of the bootstrapping. */
	public record Account(UUID id, String loginName, String status) {
	}

	private final JdbcTemplate jdbc;

	public SystemAdminAccountStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The ID of the first account; empty on an instance that is not bootstrapped. */
	public Optional<UUID> firstAccountId() {
		return Optional.ofNullable(this.jdbc.query("SELECT id FROM system_admin_accounts ORDER BY created_at, id LIMIT 1",
				(rs) -> rs.next() ? rs.getObject(1, UUID.class) : null));
	}

	/** The first account; empty on an instance that is not bootstrapped. */
	public Optional<Account> firstAccount() {
		return this.jdbc.query(
				"SELECT id, login_name, status FROM system_admin_accounts ORDER BY created_at, id LIMIT 1",
				(rs) -> rs.next() ? Optional.of(new Account(rs.getObject(1, UUID.class), rs.getString(2),
						rs.getString(3))) : Optional.<Account>empty());
	}

	/** Locks the account row ({@code FOR UPDATE}); fails for an unknown account. */
	public void lockAccount(UUID id) {
		this.jdbc.queryForObject("SELECT id FROM system_admin_accounts WHERE id = ? FOR UPDATE", UUID.class, id);
	}

	/** The login name of the account. */
	public String loginName(UUID id) {
		return this.jdbc.queryForObject("SELECT login_name FROM system_admin_accounts WHERE id = ?", String.class, id);
	}

	/** Reactivates a deactivated account; returns whether it was deactivated. */
	public boolean reactivate(UUID id) {
		return this.jdbc.update("""
				UPDATE system_admin_accounts
				SET status = CASE WHEN first_login_at IS NULL THEN 'bootstrapped' ELSE 'active' END,
				    deactivated_at = NULL, deactivated_by_admin_id = NULL, deactivated_by_person_id = NULL
				WHERE id = ? AND status = 'deactivated'""", id) == 1;
	}

}
