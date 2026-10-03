package com.api.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the nightly clean-up jobs (old notifications, expired reset links, unfinished online payments). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
