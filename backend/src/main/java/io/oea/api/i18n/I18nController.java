package io.oea.api.i18n;

import io.oea.api.generated.I18NApi;
import io.oea.app.metamodel.MetamodelLabels;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The label bundle of a locale with ETag caching (ADR-025, design D3 of us-140-dynamic-labels).
 */
@RestController
public class I18nController implements I18NApi {

	static final String CACHE_CONTROL = "max-age=300, stale-while-revalidate=3600";

	private final MetamodelLabels labels;

	public I18nController(MetamodelLabels labels) {
		this.labels = labels;
	}

	@Override
	public ResponseEntity<Map<String, String>> getI18nBundle(String locale, String ifNoneMatch) {
		MetamodelLabels.Bundle bundle = this.labels.bundle(locale);
		if (matches(ifNoneMatch, bundle.etag())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.eTag(bundle.etag())
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
				.build();
		}
		return ResponseEntity.ok().eTag(bundle.etag()).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).body(bundle.labels());
	}

	/** Weak comparison (RFC 9110 §13.1.2): {@code *}, or any listed tag equal apart from {@code W/}. */
	private static boolean matches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}
		String current = opaque(etag);
		return Arrays.stream(ifNoneMatch.split(","))
			.map(String::strip)
			.anyMatch((tag) -> "*".equals(tag) || opaque(tag).equals(current));
	}

	private static String opaque(String tag) {
		return tag.startsWith("W/") ? tag.substring(2) : tag;
	}

}
