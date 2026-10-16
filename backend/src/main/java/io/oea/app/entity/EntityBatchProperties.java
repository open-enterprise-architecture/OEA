package io.oea.app.entity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limit of the batch write (REQ-154 AC4, design D4): at most 500 entities per request, the
 * single maximum batch size of all batch endpoints; it can only be lowered.
 */
@ConfigurationProperties("oea.entities.batch")
public record EntityBatchProperties(@DefaultValue("500") int maxItems) {

	public static final int MAXIMUM = 500;

	public EntityBatchProperties {
		if (maxItems < 1 || maxItems > MAXIMUM) {
			throw new IllegalArgumentException("oea.entities.batch.max-items must be between 1 and " + MAXIMUM);
		}
	}

}
