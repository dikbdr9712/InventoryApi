package com.api.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Text messages for the few updates that matter on the move ("your order is on the way, the code is 1234").
 *
 * Works with any SMS provider that takes a simple web address, for example a bulk SMS account from a mobile operator:
 *   app.sms.enabled=true
 *   app.sms.url=https://sms.example.bt/send?key=SECRET&to=975{to}&text={text}
 * {to} becomes the 8-digit phone number and {text} the message (both made safe for a web address).
 * Switched off by default: the message is only written to the log.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    /** Made the first time a message is really sent (most servers never send SMS). */
    private volatile HttpClient http;

    @Value("${app.sms.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.url:}")
    private String urlTemplate;

    private HttpClient client() {
        if (http == null) {
            synchronized (this) {
                if (http == null) {
                    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                }
            }
        }
        return http;
    }

    public void send(String phone, String text) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return;
        }
        String number = digits.length() > 8 ? digits.substring(digits.length() - 8) : digits;
        EmailService.afterCommit(() -> deliver(number, text));
    }

    private void deliver(String number, String text) {
        if (!enabled || urlTemplate == null || urlTemplate.isBlank()) {
            log.info("SMS not sent (SMS is switched off) to {}: {}", number, text);
            return;
        }
        try {
            String url = urlTemplate
                    .replace("{to}", URLEncoder.encode(number, StandardCharsets.UTF_8))
                    .replace("{text}", URLEncoder.encode(text, StandardCharsets.UTF_8));
            HttpResponse<Void> response = client().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.warn("SMS to {} refused by the provider (HTTP {})", number, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("SMS to {} failed: {}", number, e.getMessage());
        }
    }
}
