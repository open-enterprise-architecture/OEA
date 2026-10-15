package io.oea.core.domain.settings;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Single row of instance settings (business object system-settings). Its existence marks
 * the instance as bootstrapped; the instance slug is immutable (ADR-001). Further settings
 * are mapped when they are used.
 */
@Entity
@Table(name = "system_settings")
public class SystemSettings extends NewAwareEntity<Short> {

	public static final short SINGLE_ROW = 1;

	/** Allowed instance slugs (data model CHECK, ADR-001). */
	public static final Pattern INSTANCE_SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");

	@Id
	private Short id;

	@Column(name = "instance_slug", nullable = false, updatable = false)
	private String instanceSlug;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by_admin_id")
	private UUID updatedByAdminId;

	@Override
	public Short getId() {
		return this.id;
	}

	protected SystemSettings() {
	}

	private SystemSettings(String instanceSlug, UUID adminId, Instant now) {
		this.id = SINGLE_ROW;
		this.instanceSlug = instanceSlug;
		this.updatedAt = now;
		this.updatedByAdminId = adminId;
	}

	/** Settings created by the bootstrapping with the instance slug. */
	public static SystemSettings bootstrapped(String instanceSlug, UUID adminId, Instant now) {
		if (instanceSlug == null || !INSTANCE_SLUG.matcher(instanceSlug).matches()) {
			throw new IllegalArgumentException("invalid instance slug");
		}
		return new SystemSettings(instanceSlug, adminId, now);
	}

	public String getInstanceSlug() {
		return this.instanceSlug;
	}

}
