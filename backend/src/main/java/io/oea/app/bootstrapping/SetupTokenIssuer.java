package io.oea.app.bootstrapping;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Issues the setup token at startup: after the database checks (singleton initialization),
 * before the web server accepts connections (lifecycle start).
 */
@Component
class SetupTokenIssuer implements SmartInitializingSingleton {

	private final SetupTokens tokens;

	SetupTokenIssuer(SetupTokens tokens) {
		this.tokens = tokens;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.tokens.issueIfRequired();
	}

}
