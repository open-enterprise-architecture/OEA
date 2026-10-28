package io.oea.config.web;

import io.oea.audit.AuditWriter;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Per-principal rate limits (REQ-186; design D3 of s3-throttling-and-denials). */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {

	@Bean
	RateLimiter rateLimiter(Clock clock) {
		return new RateLimiter(clock);
	}

	@Bean
	FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, AuditWriter audit,
			@Value("${oea.web.rate-limit.requests-per-minute:600}") int requestsPerMinute,
			@Value("${oea.web.rate-limit.entity-batch-per-minute:10}") int batchesPerMinute) {
		if (requestsPerMinute < 1 || batchesPerMinute < 1) {
			throw new IllegalStateException(
					"oea.web.rate-limit.requests-per-minute and oea.web.rate-limit.entity-batch-per-minute must be at least 1");
		}
		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
				new RateLimitFilter(limiter, audit, requestsPerMinute, batchesPerMinute));
		registration.addUrlPatterns("/api/v1/*");
		// after the security filter chain (principal known), before the request-size filter reads the body
		registration.setOrder(-1);
		return registration;
	}

}
