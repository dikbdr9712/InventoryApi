package com.api.inventory.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The ONLY CORS setup in the project.
 * Spring Security uses it via .cors(...) in SecurityConfig.
 */
@Configuration
public class CorsConfig {

    /**
     * Which websites may call the API from a browser. On a server set APP_CORS_ALLOWED_ORIGINS, for example
     * https://drukbazaars.bt,https://www.drukbazaars.bt (not needed when the site and the API share one address behind Nginx).
     * The default covers a developer's computer: any local port, plus two local network addresses.
     */
    @Value("${app.cors.allowed-origins:http://localhost:[*],http://127.0.0.1:[*],http://192.168.123.30:4200,http://192.168.137.1:4200}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());

        // Send the login session cookie with requests
        config.setAllowCredentials(true);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        // Browsers may cache the pre-flight check for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
