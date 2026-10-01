package com.api.inventory.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Turns on @PreAuthorize. Without this line the annotations in the controllers are ignored. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}