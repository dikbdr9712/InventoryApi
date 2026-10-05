package com.api.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Product photos at /uploads/... from the uploads folder (app.files.store=disk, the default).
 * With app.files.store=database the photos come from the database instead (UploadsController).
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.files.store:disk}")
    private String store;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if ("database".equalsIgnoreCase(store)) {
            return;
        }
        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir)
                .setCachePeriod(3600);
    }
}
