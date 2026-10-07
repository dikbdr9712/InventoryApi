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
import java.util.Base64;

/**
 * Text messages: the "forgot password" code, and the few order updates that matter on the move.
 *
 * Two kinds of SMS provider (app.sms.provider):
 *  - url (default): any provider that takes a simple web address, for example a bulk SMS account from a mobile
 *    operator in Bhutan:
 *      app.sms.enabled=true
 *      app.sms.url=https://sms.example.bt/send?key=SECRET&to=975{to}&text={text}
 *    {to} becomes the 8-digit phone number and {text} the message (both made safe for a web address).
 *  - twilio: app.sms.twilio.account-sid, app.sms.twilio.auth-token and app.sms.twilio.from (a Twilio number such
 *    as +1..., or a Messaging Service id starting with MG). Numbers are sent as +{app.sms.country-code}{8 digits}.
 * Switched off by default: the message is only written to the log (on the server only that one was not sent,
 * without the text, because it may hold a code: app.sms.log-text=false).
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    /** Made the first time a message is really sent (most servers never send SMS). */
    private volatile HttpClient http;

    @Value("${app.sms.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.provider:url}")
    private String provider;

    @Value("${app.sms.url:}")
    private String urlTemplate;

    @Value("${app.sms.country-code:975}")
    private String countryCode;

    @Value("${app.sms.twilio.account-sid:}")
    private String twilioSid;

    @Value("${app.sms.twilio.auth-token:}")
    private String twilioToken;

    @Value("${app.sms.twilio.from:}")
    private String twilioFrom;

    /** Write the text of unsent messages to the log (handy on a developer's computer; false on a server). */
    @Value("${app.sms.log-text:true}")
    private boolean logText;

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

    /** Messages really go out (SMS switched on and the provider filled in). */
    public boolean isEnabled() {
        if (!enabled) {
            return false;
        }
        return isTwilio()
                ? !blank(twilioSid) && !blank(twilioToken) && !blank(twilioFrom)
                : !blank(urlTemplate);
    }

    /** A code sent now reaches someone: the phone, or the log on a developer's computer. */
    public boolean isAvailable() {
        return isEnabled() || logText;
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
        if (!isEnabled()) {
            if (logText) {
                log.info("SMS not sent (SMS is switched off) to {}: {}", number, text);
            } else {
                log.info("SMS not sent (SMS is switched off) to ****{}", number.substring(number.length() - 4));
            }
            return;
        }
        try {
            HttpResponse<String> response = client().send(isTwilio() ? twilio(number, text) : simple(number, text),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                String reason = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
                log.warn("SMS to {} refused by the provider (HTTP {}): {}", number, response.statusCode(),
                        reason.substring(0, Math.min(300, reason.length())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("SMS to {} was interrupted", number);
        } catch (Exception e) {
            log.warn("SMS to {} failed: {}", number, e.getMessage());
        }
    }

    private HttpRequest simple(String number, String text) {
        String url = urlTemplate
                .replace("{to}", URLEncoder.encode(number, StandardCharsets.UTF_8))
                .replace("{text}", URLEncoder.encode(text, StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
    }

    private HttpRequest twilio(String number, String text) {
        String to = "+" + countryCode.replaceAll("\\D", "") + number;
        String from = twilioFrom.trim();
        String form = "To=" + enc(to)
                + (from.startsWith("MG") ? "&MessagingServiceSid=" : "&From=") + enc(from)
                + "&Body=" + enc(text);
        String login = Base64.getEncoder().encodeToString((twilioSid.trim() + ":" + twilioToken.trim()).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + enc(twilioSid.trim()) + "/Messages.json"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + login)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
    }

    private boolean isTwilio() {
        return "twilio".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
