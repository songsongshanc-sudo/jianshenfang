package com.gym.self.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class LocalUploadResourceConfig implements WebMvcConfigurer {

    private final Path root;

    public LocalUploadResourceConfig(@Value("${gym.files.public-dir}") String publicDir) {
        this.root = Path.of(publicDir);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = root.toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**", "/api/uploads/**")
                .addResourceLocations(location);
    }
}
