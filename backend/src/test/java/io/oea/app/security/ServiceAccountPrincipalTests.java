package io.oea.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The audit actor of each principal type (design D6 of s12-client-credentials-token). */
class ServiceAccountPrincipalTests {

	@Test
	void serviceAccountIsRecordedWithItsClientId() {
		UUID client = UUID.randomUUID();
		AuditEvent.Actor actor = Sessions.actor(OeaPrincipal.serviceAccount(42, client, "ci-scanner", Instant.now()));
		assertThat(actor).isEqualTo(new AuditEvent.Actor("service-account", 42L, null, client.toString()));
	}

	@Test
	void personAndSystemAdminAccountCarryNoAgentId() {
		UUID admin = UUID.randomUUID();
		assertThat(Sessions.actor(new OeaPrincipal(OeaPrincipal.Type.PERSON, null, 7L, "Anna", null, true, Instant.now())))
			.isEqualTo(new AuditEvent.Actor("person", 7L, null, null));
		assertThat(Sessions.actor(new OeaPrincipal(OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT, admin, null, "admin", null,
				true, Instant.now())))
			.isEqualTo(new AuditEvent.Actor("system-admin-account", null, admin, null));
	}

}
