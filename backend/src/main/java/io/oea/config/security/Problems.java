package io.oea.config.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** Writes RFC 9457 problems from filters, where no controller advice applies. */
public final class Problems {

	private Problems() {
	}

	public static void write(HttpServletResponse response, int status, String type, String title) throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		var body = JsonMapper.shared().createObjectNode();
		body.put("type", "urn:oea:problem:" + type);
		body.put("title", title);
		body.put("status", status);
		response.getWriter().write(body.toString());
	}

}
