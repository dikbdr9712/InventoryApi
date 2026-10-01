package com.api.inventory.config;

import java.util.List;

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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Updated to allow your local network IP alongside localhost
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://192.168.123.30:4200",  // Your main PC local IP address
                "http://192.168.137.1:4200"    // Your secondary network IP address
        ));

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
