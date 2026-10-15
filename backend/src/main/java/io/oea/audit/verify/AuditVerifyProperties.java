package io.oea.audit.verify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection of {@code oea audit verify}: read-only credentials of {@code oea_auditor}.
 *
 * @param url JDBC URL of the OEA database
 * @param username auditor role
 * @param password password of the auditor role
 */
@ConfigurationProperties("oea.audit-verify")
public record AuditVerifyProperties(String url, String username, String password) {

}
