package io.oea.app.solution;

/**
 * The predecessor of a plateau cannot change while a solution targets it (design D2 of
 * us-084-plateaus, BR-04 of the plateau). Nothing was changed.
 */
public class PlateauInUseException extends RuntimeException {

	PlateauInUseException(String name) {
		super("Solutions target the plateau '" + name + "', so its predecessor cannot change");
	}

}
