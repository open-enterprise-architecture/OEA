package io.oea.architecture.fixtures.cycle.second;

import io.oea.architecture.fixtures.cycle.first.FirstService;

/** The other side of a cycle between two packages. */
public class SecondService {

	public String run() {
		return FirstService.name();
	}

	public static String name() {
		return "second";
	}

}
