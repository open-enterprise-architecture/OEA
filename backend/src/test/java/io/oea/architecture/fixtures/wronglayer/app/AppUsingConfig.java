package io.oea.architecture.fixtures.wronglayer.app;

import io.oea.architecture.fixtures.wronglayer.config.ConfigSetting;

/** Violation: the application layer depends on the configuration layer. */
public class AppUsingConfig {

	public String run() {
		return ConfigSetting.value();
	}

}
