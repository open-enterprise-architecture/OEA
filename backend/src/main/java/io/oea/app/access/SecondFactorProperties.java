package io.oea.app.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operator floor of the enforcement of a second factor (design D1 of
 * us-031-second-factor-enforcement): while {@code enforced} is true, administrators cannot switch
 * the enforcement off.
 */
@ConfigurationProperties("oea.auth.second-factor")
public record SecondFactorProperties(@DefaultValue("false") boolean enforced) {
}
