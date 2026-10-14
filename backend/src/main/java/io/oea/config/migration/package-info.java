/**
 * Separate migration step ({@code oea migrate}) and the startup validation that the schema
 * is current (ADR-033 §2, ADR-037 §3). The running application never migrates.
 */
package io.oea.config.migration;
