package io.oea.config.security;

import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * L1 authentication (ADR-032 §8, REQ-163): only the public operations of the contract are
 * reachable without a session. Security headers and CORS per REQ-187.
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration implements WebMvcConfigurer {

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
				.contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
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
				authorize.requestMatchers("/error").permitAll().anyRequest().authenticated();
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
