package com.api.inventory.service.payments;

import com.api.inventory.entity.PaymentIntent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Pay from your bank account": the customer stays on our site (page /pay/bank), picks their bank, enters the
 * account number and the code their bank sends to their phone. BankPaymentService runs the steps through the
 * BankGatewayClient. Offered only when a client is switched on (app.payments.bank.mode = test or rma).
 */
@Component
public class BankAccountGateway implements PaymentGateway {

    public static final String CODE = "BANK";

    private final ObjectProvider<BankGatewayClient> client;

    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    public BankAccountGateway(ObjectProvider<BankGatewayClient> client) {
        this.client = client;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        BankGatewayClient c = client.getIfAvailable();
        return c != null && c.testMode()
                ? "Bank account through the RMA Payment Gateway (TEST MODE: no real money)"
                : "Bank account through the RMA Payment Gateway";
    }

    @Override
    public boolean enabled() {
        return client.getIfAvailable() != null;
    }

    @Override
    public String startUrl(PaymentIntent intent) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/pay/bank?ref=" + intent.getReference();
    }
}
