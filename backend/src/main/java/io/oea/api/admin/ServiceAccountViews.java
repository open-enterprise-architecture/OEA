package io.oea.api.admin;

import io.oea.api.generated.model.ServiceAccount;
import io.oea.api.generated.model.ServiceAccountCreated;
import io.oea.api.generated.model.ServiceAccountWrite;
import io.oea.app.access.ServiceAccounts;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Maps service accounts between the contract and {@link ServiceAccounts} (us-142-service-accounts). */
final class ServiceAccountViews {

	private ServiceAccountViews() {
	}

	static ServiceAccounts.Request request(ServiceAccountWrite write) {
		return new ServiceAccounts.Request(write.getName(), write.getDescription(),
				(write.getRoles() != null) ? List.copyOf(write.getRoles()) : null, write.getSecretValidityDays());
	}

	static ServiceAccount account(ServiceAccounts.View account) {
		return new ServiceAccount(account.clientId(), Math.toIntExact(account.personId()), account.name(),
				account.roles(), ServiceAccount.StatusEnum.fromValue(account.status()), time(account.createdAt()),
				account.secretExpiresSoon())
			.description(account.description())
			.lastUsedAt(time(account.lastUsedAt()))
			.rotatedAt(time(account.rotatedAt()))
			.secretExpiresAt(time(account.secretExpiresAt()));
	}

	static ServiceAccountCreated created(ServiceAccounts.WithSecret created) {
		ServiceAccounts.View account = created.view();
		return new ServiceAccountCreated(account.clientId(), Math.toIntExact(account.personId()), account.name(),
				account.roles(), ServiceAccountCreated.StatusEnum.fromValue(account.status()),
				time(account.createdAt()), account.secretExpiresSoon(), created.clientSecret())
			.description(account.description())
			.lastUsedAt(time(account.lastUsedAt()))
			.rotatedAt(time(account.rotatedAt()))
			.secretExpiresAt(time(account.secretExpiresAt()));
	}

	private static OffsetDateTime time(Instant instant) {
		return (instant != null) ? instant.atOffset(ZoneOffset.UTC) : null;
	}

}
