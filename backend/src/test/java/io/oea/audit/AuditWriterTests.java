package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.TestcontainersConfiguration;
import io.oea.audit.chain.ChainHasher;
import io.oea.audit.chain.ChainTable;
import io.oea.config.web.RequestIdFilter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditWriterTests {

	@Autowired
	private AuditWriter writer;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvcTester mvc;

	@Test
	void appendedEventIsChainedAndCarriesTheRequestId() {
		UUID admin = UUID.randomUUID();
		MDC.put(AuditWriter.REQUEST_ID, "req-test-1");
		AppendedLink link;
		try {
			link = this.writer.append(new AuditEvent("test.audit-writer", AuditEvent.Category.SYSTEM,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
					AuditEvent.Actor.systemAdminAccount(admin), "setting", "1", Map.of("mode", "local")));
		}
		finally {
			MDC.remove(AuditWriter.REQUEST_ID);
		}
		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT request_id, actor_type, actor_admin_id, details::text AS details, chain_seq FROM audit.events WHERE id = ?",
				link.id());
		assertThat(row).containsEntry("request_id", "req-test-1")
			.containsEntry("actor_type", "system-admin-account")
			.containsEntry("actor_admin_id", admin)
			.containsEntry("details", "{\"mode\": \"local\"}")
			.containsEntry("chain_seq", link.chainSeq());
		String recomputed = this.jdbc.query("SELECT * FROM audit.events WHERE id = ?", (rs) -> {
			rs.next();
			return HexFormat.of()
				.formatHex(ChainHasher.recordHash(rs.getInt("hash_version"), rs.getLong("chain_seq"),
						rs.getBytes("prev_hash"), ChainTable.EVENTS, ChainHasher.fieldValues(ChainTable.EVENTS, rs)));
		}, link.id());
		assertThat(recomputed).isEqualTo(HexFormat.of().formatHex(link.recordHash()));
	}

	@Test
	void eventWithExplicitSourceAddressIsChainedWithoutRequestId() {
		MDC.put(AuditWriter.REQUEST_ID, "req-ignored");
		AppendedLink link;
		try {
			link = this.writer.appendIndependently(new AuditEvent("test.audit-writer-summary",
					AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING, AuditEvent.Outcome.DENIED,
					AuditEvent.Source.API, AuditEvent.Actor.unauthenticated(), null, null,
					Map.of("summary", true, "suppressedCount", 30)), "192.0.2.10");
		}
		finally {
			MDC.remove(AuditWriter.REQUEST_ID);
		}
		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT host(source_ip) AS source_ip, request_id FROM audit.events WHERE id = ?", link.id());
		assertThat(row).containsEntry("source_ip", "192.0.2.10").containsEntry("request_id", null);
		String recomputed = this.jdbc.query("SELECT * FROM audit.events WHERE id = ?", (rs) -> {
			rs.next();
			return HexFormat.of()
				.formatHex(ChainHasher.recordHash(rs.getInt("hash_version"), rs.getLong("chain_seq"),
						rs.getBytes("prev_hash"), ChainTable.EVENTS, ChainHasher.fieldValues(ChainTable.EVENTS, rs)));
		}, link.id());
		assertThat(recomputed).isEqualTo(HexFormat.of().formatHex(link.recordHash()));
	}

	@Test
	void requestIdIsEchoedOrGenerated() {
		assertThat(this.mvc.get().uri("/api/v1/health").header(RequestIdFilter.HEADER, "abc-123"))
			.headers()
			.hasValue(RequestIdFilter.HEADER, "abc-123");
		assertThat(this.mvc.get().uri("/api/v1/health").header(RequestIdFilter.HEADER, "bad value\n"))
			.headers()
			.satisfies((headers) -> assertThat(headers.getFirst(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}"));
	}

}
