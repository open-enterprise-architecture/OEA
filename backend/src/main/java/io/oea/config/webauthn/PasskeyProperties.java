package io.oea.config.webauthn;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Relying party of passkeys (design D1 of us-022-passkeys). Empty values are derived from
 * {@code oea.web.public-url}.
 * @param rpId relying party ID, by default the host of the public URL
 * @param rpName name shown by the authenticator
 * @param origins allowed origins, by default the public URL without path
 */
@ConfigurationProperties("oea.auth.passkey")
public record PasskeyProperties(@DefaultValue("") String rpId, @DefaultValue("OEA") String rpName,
		@DefaultValue({}) List<String> origins) {
}
