/**
 * Technical adapters without domain knowledge (ADR-028, amendment 2026-11-13): the OIDC client,
 * the WebAuthn verifier, the secret cipher with its key ring and the configuration guards. The
 * layer may be used by every other layer except core, and depends on no other OEA package.
 */
package io.oea.infrastructure;
