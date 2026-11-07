package io.oea.config.security;

import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * L1 authentication (ADR-032 §8, REQ-163): only the public operations of the contract are
 * reachable without a session. Security headers and CORS per REQ-187. Outside {@code /api/},
 * {@code GET} and {@code HEAD} are open: there the backend serves only the files of the web
 * portal, whose login runs in the portal, with the portal's content security policy (design D3,
 * D4 of s9-portal-catalogs).
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration implements WebMvcConfigurer {

	/** Content security policy of the API responses. */
	public static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

	/**
	 * Content security policy of the web portal, shared with the frontend smoke test (D4). Styles
	 * allow 'unsafe-inline': Nuxt UI writes its color variables as a style element at runtime
	 * ("nuxt-ui-colors"), which the smoke test reported; scripts stay 'self' only.
	 */
	public static final String PORTAL_CONTENT_SECURITY_POLICY = portalPolicy();

	private static final RequestMatcher API = (request) -> api(request);

	private static final RequestMatcher PORTAL = (request) -> !api(request);

	/** Reads of portal files; actuator paths stay closed even though no endpoint is exposed. */
	private static final RequestMatcher PORTAL_READ = (request) -> !api(request) && !actuator(request)
			&& ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));

	private final ContractAuthorizationRules rules;

	private final PermissionResolver permissions;

	private final PermissionDenials denials;

	public SecurityConfiguration(ContractAuthorizationRules rules, PermissionResolver permissions,
			PermissionDenials denials) {
		this.rules = rules;
		this.permissions = permissions;
		this.denials = denials;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Sessions sessions,
			@Value("${oea.security.cors.allowed-origins:}") List<String> allowedOrigins) throws Exception {
		http.csrf((csrf) -> csrf.disable())
			.httpBasic((basic) -> basic.disable())
			.formLogin((form) -> form.disable())
			.logout((logout) -> logout.disable())
			.requestCache((cache) -> cache.disable())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors((cors) -> cors.configurationSource(cors(allowedOrigins)))
			.headers((headers) -> headers
				.httpStrictTransportSecurity((hsts) -> hsts.includeSubDomains(true)
					.maxAgeInSeconds(31536000)
					.requestMatcher((request) -> true))
				.contentTypeOptions((options) -> {
				})
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.NO_REFERRER))
				.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(API,
						new ContentSecurityPolicyHeaderWriter(API_CONTENT_SECURITY_POLICY)))
				.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(PORTAL,
						new ContentSecurityPolicyHeaderWriter(PORTAL_CONTENT_SECURITY_POLICY)))
				.frameOptions((frame) -> frame.deny()))
			.addFilterBefore(new OeaSessionFilter(sessions), AnonymousAuthenticationFilter.class)
			.addFilterAfter(new CsrfOriginFilter(allowedOrigins), OeaSessionFilter.class)
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint((request, response, ex) -> Problems.write(response,
						HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", "Authentication required"))
				.accessDeniedHandler((request, response, ex) -> Problems.write(response,
						HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden")))
			.authorizeHttpRequests((authorize) -> {
				for (ContractAuthorizationRules.PublicEndpoint endpoint : this.rules.publicEndpoints()) {
					authorize.requestMatchers(HttpMethod.valueOf(endpoint.method()), endpoint.pathPattern()).permitAll();
				}
				authorize.requestMatchers("/error").permitAll()
					.requestMatchers(PORTAL_READ)
					.permitAll()
					.anyRequest()
					.authenticated();
			});
		return http.build();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthorizationInterceptor(this.rules, this.permissions, this.denials))
			.addPathPatterns(ContractAuthorizationRules.API_PREFIX + "/**");
		registry.addInterceptor(new NotImplementedInterceptor())
			.addPathPatterns(ContractAuthorizationRules.API_PREFIX + "/**");
	}

	private static boolean api(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals("/api") || path.startsWith("/api/");
	}

	private static boolean actuator(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals("/actuator") || path.startsWith("/actuator/");
	}

	private static String portalPolicy() {
		try {
			return new ClassPathResource("portal/content-security-policy.txt").getContentAsString(StandardCharsets.UTF_8)
				.strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static UrlBasedCorsConfigurationSource cors(List<String> allowedOrigins) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (!allowedOrigins.isEmpty()) {
			CorsConfiguration configuration = new CorsConfiguration();
			configuration.setAllowedOrigins(allowedOrigins);
			configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
			configuration.setAllowedHeaders(List.of("Content-Type", CsrfOriginFilter.HEADER, "X-Request-Id"));
			configuration.setAllowCredentials(true);
			source.registerCorsConfiguration("/**", configuration);
		}
		return source;
	}

}
