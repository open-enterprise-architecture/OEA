package io.oea.app.bootstrapping;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Setup token handover (REQ-017, design D1).
 *
 * @param setupTokenFile file inside the container that receives the token; readable only by
 * the OEA user
 * @param setupTokenValidity validity of a token
 */
@ConfigurationProperties("oea.bootstrap")
public record BootstrapProperties(@DefaultValue("/run/oea/setup-token") Path setupTokenFile,
		@DefaultValue("PT24H") Duration setupTokenValidity) {

}
