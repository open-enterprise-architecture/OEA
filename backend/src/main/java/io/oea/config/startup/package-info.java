/**
 * Startup checks that refuse unsafe configurations (ADR-036 §6, REQ-183, REQ-156): the
 * configuration guards G4-G6 and the inverse development guard run on the prepared
 * environment, the database checks before the web server accepts connections.
 */
package io.oea.config.startup;
