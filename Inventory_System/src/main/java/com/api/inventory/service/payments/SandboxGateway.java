package com.api.inventory.service.payments;

import com.api.inventory.entity.PaymentIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A pretend gateway for trying the whole online payment flow without real money: the customer gets a test page
 * (Pay / Fail / Cancel) inside our own site. Switched on with app.payments.sandbox.enabled=true (the default on a
 * developer's computer). It must be switched OFF on the live site (application-prod.properties does that).
 */
@Component
public class SandboxGateway implements PaymentGateway {

    public static final String CODE = "SANDBOX";

    @Value("${app.payments.sandbox.enabled:true}")
    private boolean enabled;

    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Test payment (no real money)";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String startUrl(PaymentIntent intent) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/pay/test?ref=" + intent.getReference();
    }
}
