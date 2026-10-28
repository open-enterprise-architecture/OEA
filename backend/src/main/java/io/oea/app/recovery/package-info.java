/**
 * Break-glass recovery of the local system admin account for a complete lockout (UC-02 E4,
 * REQ-173 AC4; change us-019-admin-safeguards, design D5). Runs as the host-side command
 * {@code admin recover}, never through the web API.
 */
package io.oea.app.recovery;
