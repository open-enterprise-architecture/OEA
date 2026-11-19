package io.oea.app.metamodel;

import io.oea.core.metamodel.ViewpointStore.SizeRow;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of the default sizes of a viewpoint (s17-viewpoint-default-sizes D3), shared by the
 * API and the package import: a size names an allowed metatype once; an element metatype needs a
 * width and a height and no length, a connection metatype needs a length and neither width nor
 * height; every value is 1 to 2000 pt. Messages never repeat the content of the request.
 */
final class ViewpointSizes {

	/** Default width of an element shape in pt, without a stored size. */
	static final int DEFAULT_WIDTH = 90;

	/** Default height of an element shape in pt, without a stored size. */
	static final int DEFAULT_HEIGHT = 30;

	/** Default length of a connection in pt, without a stored size. */
	static final int DEFAULT_LENGTH = 90;

	/** Upper bound of a size in pt. */
	static final int MAX = 2000;

	private ViewpointSizes() {
	}

	/** The first rule the sizes violate, or empty if they are valid. */
	static Optional<String> problem(Set<UUID> elementTypes, Set<UUID> connectionTypes, List<SizeRow> sizes) {
		Set<UUID> seen = new HashSet<>();
		for (SizeRow size : sizes) {
			boolean connection = connectionTypes.contains(size.metatypeId());
			if (!connection && !elementTypes.contains(size.metatypeId())) {
				return Optional.of("A size names a metatype the viewpoint does not allow.");
			}
			if (!seen.add(size.metatypeId())) {
				return Optional.of("A metatype has more than one size.");
			}
			if (connection) {
				if (size.width() != null || size.height() != null || size.length() == null) {
					return Optional.of("A connection metatype needs a length and no width or height.");
				}
			}
			else if (size.width() == null || size.height() == null || size.length() != null) {
				return Optional.of("An element metatype needs a width and a height and no length.");
			}
			if (!inRange(size.width()) || !inRange(size.height()) || !inRange(size.length())) {
				return Optional.of("A size is 1 to " + MAX + " pt.");
			}
		}
		return Optional.empty();
	}

	private static boolean inRange(Integer value) {
		return value == null || (value >= 1 && value <= MAX);
	}

}
