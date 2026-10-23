package io.oea.config.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Size limits of request bodies (REQ-186, REQ-184; design D1 of s2b-backend-hardening). */
@Configuration(proxyBeanMethods = false)
public class RequestSizeConfiguration {

	/** Path that accepts metamodel packages. */
	public static final String IMPORT_PATH = "/api/v1/admin/metamodel/import";

	/** Path of the entity batch. */
	public static final String BATCH_PATH = "/api/v1/entities/batch";

	@Bean
	FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
			@Value("${oea.web.request-size.default:1MB}") DataSize defaultSize,
			@Value("${oea.web.request-size.entity-batch:10MB}") DataSize batchSize,
			@Value("${oea.metamodel.import.max-size:10MB}") DataSize packageSize) {
		RequestSizeLimitFilter.Limit requestLimit = new RequestSizeLimitFilter.Limit(defaultSize.toBytes(),
				"request-too-large", "Request too large");
		FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>(
				new RequestSizeLimitFilter(requestLimit,
						Map.of(BATCH_PATH,
								new RequestSizeLimitFilter.Limit(batchSize.toBytes(), "request-too-large",
										"Request too large"),
								IMPORT_PATH, new RequestSizeLimitFilter.Limit(packageSize.toBytes(), "package-too-large",
										"Package too large"))));
		registration.addUrlPatterns("/api/v1/*");
		// after the security filter chain: unauthenticated requests get 401 first
		registration.setOrder(0);
		return registration;
	}

}
