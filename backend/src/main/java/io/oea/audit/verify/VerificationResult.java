package io.oea.audit.verify;

import java.util.List;

/** Outcome of a chain verification; {@code problems} is empty for an intact chain. */
public record VerificationResult(long links, long headSeq, String headHash, List<String> problems) {

	public boolean intact() {
		return this.problems.isEmpty();
	}

}
