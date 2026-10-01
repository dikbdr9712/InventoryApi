package com.api.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Use the CorsConfigurationSource bean from CorsConfig (allows Angular on :4200)
                .cors(Customizer.withDefaults())

                // Angular sends JSON with the session cookie; CSRF tokens are not used
                .csrf(csrf -> csrf.disable())

                // Login is handled by your own /api/auth/login controller
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // Same as before: everything is reachable; controllers check the session themselves
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}