package com.dharanijayachandran.clinicbooking.config;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular app baked into the jar, with SPA deep-link support.
 *
 * Client-side routes like /book only exist in the browser's router, so a hard
 * refresh or a pasted link asks the server for a file that was never built.
 * Anything that doesn't resolve to a real file therefore falls back to
 * index.html and lets Angular route it.
 *
 * The exclusions matter: without them that same fallback would swallow
 * unknown API paths and answer them with a page of HTML, so a typo'd endpoint
 * would return 200 and an Angular shell instead of an honest 404 — miserable
 * to debug from the client side.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final List<String> SERVER_PREFIXES =
            List.of("api/", "ws", "v3/api-docs", "swagger-ui", "actuator/");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        if (SERVER_PREFIXES.stream().anyMatch(resourcePath::startsWith)) {
                            return null;
                        }

                        ClassPathResource index = new ClassPathResource("static/index.html");
                        // No SPA bundled (e.g. the backend-only compose build):
                        // behave like a plain API rather than 500 on every path.
                        return index.exists() ? index : null;
                    }
                });
    }
}
