/**
 * Startup checks that refuse unsafe configurations (ADR-036 §6, REQ-183, REQ-156): the
 * database checks before the web server accepts connections, the guard of the stored
 * providers and the failure analyzer. The configuration guards on the prepared environment
 * are in {@code io.oea.infrastructure.guards}.
 */
package io.oea.config.startup;
