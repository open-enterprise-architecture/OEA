package io.oea.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every controller of the API layer under the server base path of
 * {@code api/openapi.yaml} ({@code /api/v1}), so generated interfaces can keep relative paths.
 */
@Configuration(proxyBeanMethods = false)
public class ApiPathConfiguration implements WebMvcConfigurer {

	/** Base path from the {@code servers} entry of the OpenAPI contract. */
	public static final String API_BASE_PATH = "/api/v1";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_BASE_PATH, HandlerTypePredicate.forBasePackage("io.oea.api"));
	}

}
