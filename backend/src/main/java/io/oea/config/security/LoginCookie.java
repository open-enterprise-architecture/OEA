package io.oea.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * The login cookie {@code __Host-OEA_LOGIN} that binds a running OIDC login to the browser
 * (design D2 of us-001-oidc-login). {@code SameSite=Lax} because the callback is a top-level
 * navigation from the IdP; it carries no authority and is valid for one login only.
 */
public final class LoginCookie {

	public static final String NAME = "__Host-OEA_LOGIN";

	public static final Duration LIFETIME = Duration.ofMinutes(10);

	private LoginCookie() {
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

	public static void write(HttpServletResponse response, String value) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(value, LIFETIME).toString());
	}

	public static void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).path("/").sameSite("Lax").maxAge(maxAge).build();
	}

}
