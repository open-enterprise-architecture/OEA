/**
 * OIDC client of the login (ADR-006, design D1 of us-001-oidc-login): discovery, the
 * authorization request with PKCE, token requests and ID token validation. Signature
 * verification always uses the provider's published keys and asymmetric algorithms (G1).
 */
package io.oea.config.oidc;
