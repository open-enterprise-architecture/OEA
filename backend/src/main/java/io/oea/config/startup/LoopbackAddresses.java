package io.oea.config.startup;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

/** Recognizes loopback hosts as defined by guard G2 (ADR-036 §6). */
final class LoopbackAddresses {

	private LoopbackAddresses() {
	}

	/** True for {@code localhost}, {@code *.localhost}, 127.0.0.0/8, {@code ::1}, {@code 0.0.0.0} and names resolving only to loopback. */
	static boolean isLoopbackHost(String host) {
		if (host == null || host.isBlank()) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		if (normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.equals("0.0.0.0")) {
			return true;
		}
		try {
			InetAddress[] addresses = InetAddress.getAllByName(normalized);
			return addresses.length > 0 && Arrays.stream(addresses).allMatch(InetAddress::isLoopbackAddress);
		}
		catch (UnknownHostException ex) {
			return false;
		}
	}

	static boolean isLoopbackUrl(String url) {
		try {
			return isLoopbackHost(URI.create(url.strip()).getHost());
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
