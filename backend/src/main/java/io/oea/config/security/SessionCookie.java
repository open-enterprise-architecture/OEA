package io.oea.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** The session cookie {@code __Host-OEA_SESSION} (REQ-165, REQ-187 AC4). */
public final class SessionCookie {

	public static final String NAME = "__Host-OEA_SESSION";

	private SessionCookie() {
	}

	public static String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public static void write(HttpServletResponse response, String token, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, maxAge).toString());
	}

	public static void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.path("/")
			.sameSite("Strict")
			.maxAge(maxAge)
			.build();
	}

}
