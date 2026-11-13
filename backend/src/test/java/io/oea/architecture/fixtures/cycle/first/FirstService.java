package io.oea.architecture.fixtures.cycle.first;

import io.oea.architecture.fixtures.cycle.second.SecondService;

/** One side of a cycle between two packages. */
public class FirstService {

	public String run() {
		return SecondService.name();
	}

	public static String name() {
		return "first";
	}

}
