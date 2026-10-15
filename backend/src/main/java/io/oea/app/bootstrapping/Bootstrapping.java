package io.oea.app.bootstrapping;

import io.oea.app.bootstrapping.BootstrapRejectedException.Reason;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.BootstrapSetupToken;
import io.oea.core.domain.auth.LocalCredential;
import io.oea.core.domain.auth.PasswordCredential;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.domain.settings.SystemSettings;
import io.oea.core.repository.BootstrapSetupTokenRepository;
import io.oea.core.repository.LocalCredentialRepository;
import io.oea.core.repository.PasswordCredentialRepository;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.repository.SystemSettingsRepository;
import io.oea.core.service.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local bootstrapping (UC-02 basic flow, design D2/D3): exactly one successful run per
 * instance; every attempt is audited, rejections in their own transaction.
 */
@Service
public class Bootstrapping {

	private final SetupTokens setupTokens;

	private final BootstrapSetupTokenRepository tokens;

	private final SystemSettingsRepository settings;

	private final SystemAdminAccountRepository accounts;

	private final LocalCredentialRepository credentials;

	private final PasswordCredentialRepository passwords;

	private final PasswordHasher hasher;

	private final AuditWriter audit;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public Bootstrapping(SetupTokens setupTokens, BootstrapSetupTokenRepository tokens,
			SystemSettingsRepository settings, SystemAdminAccountRepository accounts,
			LocalCredentialRepository credentials, PasswordCredentialRepository passwords, PasswordHasher hasher,
			AuditWriter audit, PlatformTransactionManager transactionManager, Clock clock) {
		this.setupTokens = setupTokens;
		this.tokens = tokens;
		this.settings = settings;
		this.accounts = accounts;
		this.credentials = credentials;
		this.passwords = passwords;
		this.hasher = hasher;
		this.audit = audit;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public boolean setupRequired() {
		return this.setupTokens.setupRequired();
	}

	/**
	 * Creates the system admin account and the instance settings.
	 * @throws BootstrapRejectedException if the instance is bootstrapped already, the token is
	 * invalid or expired, or the input is invalid
	 */
	public BootstrapResult bootstrap(BootstrapCommand command) {
		try {
			return this.transaction.execute((status) -> bootstrapInTransaction(command));
		}
		catch (DataIntegrityViolationException ex) {
			// lost the race on the single settings row (design D2)
			throw rejected(Reason.ALREADY_BOOTSTRAPPED, List.of());
		}
		catch (BootstrapRejectedException ex) {
			throw rejected(ex.getReason(), ex.getInvalidFields());
		}
	}

	private BootstrapResult bootstrapInTransaction(BootstrapCommand command) {
		if (this.settings.existsById(SystemSettings.SINGLE_ROW)) {
			throw new BootstrapRejectedException(Reason.ALREADY_BOOTSTRAPPED, List.of());
		}
		Instant now = this.clock.instant();
		Optional<BootstrapSetupToken> token = this.tokens.findForUpdate(BootstrapSetupToken.SINGLE_ROW);
		if (token.isEmpty()) {
			// a competing bootstrapping completed while we waited for the lock
			Reason reason = this.settings.existsById(SystemSettings.SINGLE_ROW) ? Reason.ALREADY_BOOTSTRAPPED
					: Reason.INVALID_SETUP_TOKEN;
			throw new BootstrapRejectedException(reason, List.of());
		}
		if (command.setupToken() == null || !token.get().matches(SetupTokens.hash(command.setupToken()), now)) {
			throw new BootstrapRejectedException(Reason.INVALID_SETUP_TOKEN, List.of());
		}
		List<String> invalid = invalidFields(command);
		if (!invalid.isEmpty()) {
			throw new BootstrapRejectedException(Reason.VALIDATION_FAILED, invalid);
		}

		SystemAdminAccount account = this.accounts.save(SystemAdminAccount.bootstrapped(command.loginName(), now));
		LocalCredential credential = this.credentials.save(LocalCredential.password(account, now));
		this.passwords.save(PasswordCredential.selfSet(credential, this.hasher.hash(command.password())));
		this.settings.saveAndFlush(SystemSettings.bootstrapped(command.instanceSlug(), account.getId(), now));
		this.tokens.delete(token.get());
		this.audit.append(new AuditEvent("system.bootstrapped", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, AuditEvent.Actor.systemAdminAccount(account.getId()),
				"system-admin-account", account.getId().toString(),
				Map.of("mode", "local", "instanceSlug", command.instanceSlug())));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				Bootstrapping.this.setupTokens.deleteFile();
			}
		});
		return new BootstrapResult(account.getId(), account.getLoginName(), command.instanceSlug());
	}

	private BootstrapRejectedException rejected(Reason reason, List<String> invalidFields) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("mode", "local");
		details.put("reason", reason.code());
		if (!invalidFields.isEmpty()) {
			details.put("fields", invalidFields);
		}
		this.audit.appendIndependently(new AuditEvent("system.bootstrap-rejected", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.DENIED, AuditEvent.Source.API,
				AuditEvent.Actor.unauthenticated(), null, null, details));
		return new BootstrapRejectedException(reason, invalidFields);
	}

	private static List<String> invalidFields(BootstrapCommand command) {
		List<String> invalid = new ArrayList<>();
		if (command.loginName() == null || !SystemAdminAccount.LOGIN_NAME.matcher(command.loginName()).matches()) {
			invalid.add("loginName");
		}
		String password = command.password();
		if (password == null || password.length() < PasswordHasher.MINIMUM_LENGTH
				|| password.length() > PasswordHasher.MAXIMUM_LENGTH) {
			invalid.add("password");
		}
		if (command.instanceSlug() == null || !SystemSettings.INSTANCE_SLUG.matcher(command.instanceSlug()).matches()) {
			invalid.add("instanceSlug");
		}
		return invalid;
	}

}
