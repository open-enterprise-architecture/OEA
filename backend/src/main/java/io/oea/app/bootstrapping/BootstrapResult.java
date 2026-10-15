package io.oea.app.bootstrapping;

import java.util.UUID;

/** Outcome of a successful bootstrapping. */
public record BootstrapResult(UUID systemAdminAccountId, String loginName, String instanceSlug) {

}
