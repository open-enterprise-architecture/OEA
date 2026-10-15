package io.oea.testsupport.auth;

import java.net.CookieHandler;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Minimal cookie store for one login flow. Like browsers, it treats loopback origins as
 * secure contexts and therefore returns {@code Secure} cookies over {@code http}, which the
 * JDK {@link java.net.CookieManager} does not. Only for the development realm on loopback.
 */
final class LoopbackCookieJar extends CookieHandler {

	private final Map<String, Cookie> cookies = new ConcurrentHashMap<>();

	private record Cookie(String host, String path, String value) {
	}

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
		String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
		String header = this.cookies.entrySet()
			.stream()
			.filter((e) -> e.getValue().host().equalsIgnoreCase(uri.getHost()) && path.startsWith(e.getValue().path()))
			.map((e) -> e.getKey() + "=" + e.getValue().value())
			.collect(Collectors.joining("; "));
		return header.isEmpty() ? Map.of() : Map.of("Cookie", List.of(header));
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders) {
		responseHeaders.forEach((name, values) -> {
			if ("Set-Cookie".equalsIgnoreCase(name)) {
				values.forEach((value) -> store(uri, value));
			}
		});
	}

	private void store(URI uri, String header) {
		String[] parts = header.split(";");
		int separator = parts[0].indexOf('=');
		if (separator <= 0) {
			return;
		}
		String name = parts[0].substring(0, separator).strip();
		String value = parts[0].substring(separator + 1).strip();
		String path = "/";
		boolean expired = value.isEmpty();
		for (int i = 1; i < parts.length; i++) {
			String attribute = parts[i].strip();
			if (attribute.regionMatches(true, 0, "Path=", 0, 5)) {
				path = attribute.substring(5);
			}
			else if (attribute.equalsIgnoreCase("Max-Age=0")) {
				expired = true;
			}
		}
		if (expired) {
			this.cookies.remove(name);
		}
		else {
			this.cookies.put(name, new Cookie(uri.getHost(), path, value));
		}
	}

}
