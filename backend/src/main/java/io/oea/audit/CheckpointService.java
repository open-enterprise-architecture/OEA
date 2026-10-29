package io.oea.audit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Checkpoints of the chain head outside the database (ADR-033 §6): periodically and at
 * orderly shutdown an {@code audit.checkpoint} record is appended and its chain position is
 * written to the application log, where the verification command can pick it up.
 */
@Component
public class CheckpointService implements SmartLifecycle {

	static final Duration MINIMUM_INTERVAL = Duration.ofMinutes(5);

	static final Duration MAXIMUM_INTERVAL = Duration.ofMinutes(1440);

	private static final Logger logger = LoggerFactory.getLogger(CheckpointService.class);

	private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)
		.withZone(ZoneOffset.UTC);

	private final AuditWriter writer;

	private final Duration interval;

	private ScheduledExecutorService scheduler;

	@Autowired
	public CheckpointService(AuditWriter writer,
			@Value("${oea.audit.checkpoint-interval:PT60M}") Duration interval) {
		this(writer, interval, true);
	}

	private CheckpointService(AuditWriter writer, Duration interval, boolean checkRange) {
		if (checkRange && (interval.compareTo(MINIMUM_INTERVAL) < 0 || interval.compareTo(MAXIMUM_INTERVAL) > 0)) {
			throw new IllegalArgumentException("oea.audit.checkpoint-interval must be between 5 and 1440 minutes");
		}
		this.writer = writer;
		this.interval = interval;
	}

	/** Without range check; only for tests of the scheduling. */
	static CheckpointService withUncheckedInterval(AuditWriter writer, Duration interval) {
		return new CheckpointService(writer, interval, false);
	}

	/** Appends a checkpoint record and logs its chain position. */
	public AppendedLink checkpoint() {
		AppendedLink link = this.writer.append(new AuditEvent("audit.checkpoint", AuditEvent.Category.SYSTEM,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.JOB, AuditEvent.Actor.system(),
				null, null, Map.of()));
		String hash = HexFormat.of().formatHex(link.recordHash());
		String at = AT.format(Instant.now());
		// fields for JSON logs, unchanged message for plain text (design D3 of s3-structured-logging-and-icons)
		logger.atInfo()
			.addKeyValue("event", "audit.checkpoint")
			.addKeyValue("chainSeq", link.chainSeq())
			.addKeyValue("recordHash", hash)
			.addKeyValue("at", at)
			.log("event=audit.checkpoint chainSeq={} recordHash={} at={}", link.chainSeq(), hash, at);
		return link;
	}

	@Override
	public void start() {
		this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("oea-audit-checkpoint").daemon().factory());
		long millis = this.interval.toMillis();
		this.scheduler.scheduleWithFixedDelay(this::checkpointSafely, millis, millis, TimeUnit.MILLISECONDS);
	}

	@Override
	public void stop() {
		this.scheduler.shutdownNow();
		this.scheduler = null;
		// Orderly shutdown: final checkpoint while the data source is still open.
		checkpointSafely();
	}

	@Override
	public boolean isRunning() {
		return this.scheduler != null;
	}

	private void checkpointSafely() {
		try {
			checkpoint();
		}
		catch (RuntimeException ex) {
			logger.warn("Audit checkpoint failed: {}", ex.getMessage());
		}
	}

}
