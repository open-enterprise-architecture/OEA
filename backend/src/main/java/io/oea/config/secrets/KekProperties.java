package io.oea.config.secrets;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key-encryption keys (REQ-188): files with a Base64-encoded 256-bit key, outside the
 * database and outside its backups.
 *
 * @param current key used for new encryptions
 * @param previous keys still accepted for decryption (rotation, {@code oea secrets reencrypt})
 */
@ConfigurationProperties("oea.security.kek")
public record KekProperties(Path current, List<Path> previous) {

	public KekProperties {
		previous = (previous != null) ? List.copyOf(previous) : List.of();
	}

}
