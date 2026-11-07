package io.oea.config.web;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built web portal from {@code oea.portal.directory} (design D3 of
 * s9-portal-catalogs, #76): the hashed files under {@code /assets/} are cached for a year, every
 * other file and {@code index.html} are revalidated. A path outside {@code /api/} without a file
 * extension that matches no file gets {@code index.html}, so links into the portal survive a
 * reload; a missing asset stays 404. Without the property, no portal is served (development
 * uses the Vite server).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${oea.portal.directory:}'.isBlank()")
public class PortalConfiguration implements WebMvcConfigurer {

	private static final String INDEX = "index.html";

	private final Path directory;

	PortalConfiguration(@Value("${oea.portal.directory}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = this.directory.toUri().toString();
		registry.addResourceHandler("/assets/**")
			.addResourceLocations(location + "assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
			.resourceChain(false)
			.addResolver(new PathResourceResolver());
		registry.addResourceHandler("/**")
			.addResourceLocations(location)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(false)
			.addResolver(new PortalResolver(new FileSystemResource(this.directory.resolve(INDEX))));
	}

	/** The resource handler does not resolve the empty path, so {@code /} forwards to the index. */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("forward:/" + INDEX);
	}

	/** Resolves the portal's files and falls back to {@code index.html} for its routes. */
	private static final class PortalResolver extends PathResourceResolver {

		private final Resource index;

		PortalResolver(Resource index) {
			this.index = index;
		}

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
				return null;
			}
			Resource resource = super.getResource(resourcePath, location);
			if (resource != null) {
				return resource;
			}
			String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
			return (name.contains(".") || !this.index.isReadable()) ? null : this.index;
		}

	}

}
