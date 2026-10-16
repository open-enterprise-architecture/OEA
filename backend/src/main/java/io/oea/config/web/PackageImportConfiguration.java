package io.oea.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Limits of package imports (REQ-184, design D3 of us-033-import-metamodel). */
@Configuration(proxyBeanMethods = false)
public class PackageImportConfiguration {

	/** Paths that accept metamodel packages. */
	public static final String IMPORT_PATH = "/api/v1/admin/metamodel/import";

	@Bean
	FilterRegistrationBean<PackageSizeLimitFilter> packageSizeLimitFilter(
			@Value("${oea.metamodel.import.max-size:10MB}") DataSize maximum) {
		FilterRegistrationBean<PackageSizeLimitFilter> registration = new FilterRegistrationBean<>(
				new PackageSizeLimitFilter(maximum.toBytes()));
		registration.addUrlPatterns(IMPORT_PATH);
		// after the security filter chain: unauthenticated requests get 401 first
		registration.setOrder(0);
		return registration;
	}

}
