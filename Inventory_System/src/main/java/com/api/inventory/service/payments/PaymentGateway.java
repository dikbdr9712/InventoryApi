package com.api.inventory.service.payments;

import com.api.inventory.entity.PaymentIntent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * A way to pay online (a bank's or the national payment gateway). To add one:
 *  1. Get a merchant account and the gateway's technical documents (test and live addresses, keys).
 *  2. Write a class that implements this interface (see SandboxGateway for the shape) and mark it @Component.
 *     startUrl sends the customer to the gateway with our reference and the amount (signed as the gateway asks).
 *     verifyCallback checks the gateway's answer really comes from the gateway (its signature) and reads it.
 *  3. Give the gateway our callback address: https://YOUR-SITE/api/online-payments/callback/{code}
 *     and the return address for the customer: https://YOUR-SITE/payment/result?ref={reference}
 * Everything after that (confirming the order, stock, sellers, emails) is already handled by OnlinePaymentService.
 */
public interface PaymentGateway {

    /** Short name stored with each payment, for example SANDBOX or RMA. */
    String code();

    /** What the customer sees, for example "Pay with your bank account". */
    String label();

    boolean enabled();

    /** Where to send the customer to pay this attempt. */
    String startUrl(PaymentIntent intent);

    /**
     * The gateway's message about a payment (server to server). Return empty when the message is not genuine.
     * Never trust the amount or result before the signature is checked.
     */
    default Optional<Result> verifyCallback(Map<String, String> params) {
        return Optional.empty();
    }

    /** What the gateway says happened. amount = what it actually charged. */
    record Result(String reference, boolean paid, String providerReference, BigDecimal amount, String message) {
    }
}
