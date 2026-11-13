package io.oea.architecture.fixtures.wronglayer.core;

import io.oea.architecture.fixtures.wronglayer.audit.AuditRecord;

/** Violation: the core layer depends on the audit layer. */
public class CoreUsingAudit {

	public String run() {
		return AuditRecord.type();
	}

}
