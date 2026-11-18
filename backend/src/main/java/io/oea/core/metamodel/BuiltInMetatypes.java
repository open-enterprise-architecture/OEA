package io.oea.core.metamodel;

import java.util.UUID;

/**
 * The metatypes the instance metamodel brings itself (scope {@code built-in}, migration V42): they
 * cannot be changed, deleted or mapped to, and entities of them are written only through their own
 * operations.
 */
public final class BuiltInMetatypes {

	/**
	 * The element metatype "Structuring aid" (US-136): a purely navigational grouping that has no
	 * properties and no connections.
	 */
	public static final UUID STRUCTURING_AID = UUID.fromString("c3f523b8-f07f-5475-9ab0-6f2ea39b537e");

	private BuiltInMetatypes() {
	}

}
