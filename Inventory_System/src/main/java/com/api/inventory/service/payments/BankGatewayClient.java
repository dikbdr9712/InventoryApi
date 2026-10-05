package com.api.inventory.service.payments;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the payment gateway that moves money from a customer's bank account to ours (in Bhutan: the RMA
 * Payment Gateway). The gateway does the banking: the customer's bank checks the account, sends the one-time code
 * to the phone registered with that account, and debits it when the right code is given. We only pass the
 * customer's answers along and learn the result.
 *
 *   1. start        register the payment (our reference and the amount) -> the gateway's transaction number
 *   2. requestCode  bank + account number -> the bank sends the one-time code to the account holder's phone
 *   3. debit        the code -> the money moves to our (the merchant's) account, with the bank's journal number,
 *                   or a reason why not
 *
 * Two versions:
 *   TestBankGatewayClient  app.payments.bank.mode=test. No bank is contacted, no money moves, no text is sent;
 *                          the code is always 123456. For trying the screens. Refused on the live site.
 *   RmaBankGatewayClient   app.payments.bank.mode=rma. The RMA Payment Gateway, with DK/Phar's merchant id and key.
 *
 * Implementations must never store or log the account number or the code.
 */
public interface BankGatewayClient {

    /** A bank the customer can pay from: the gateway's code, a short name for the card on screen, the full name. */
    record Bank(String code, String shortName, String name) {

        /** "code:short:name" (or "code:name") entries, comma separated, as in app.payments.bank.banks. */
        public static List<Bank> parse(String list) {
            List<Bank> banks = new ArrayList<>();
            for (String entry : list.split(",")) {
                String[] parts = entry.trim().split(":", 3);
                if (parts.length == 3 && !parts[0].isBlank() && !parts[2].isBlank()) {
                    banks.add(new Bank(parts[0].trim(), parts[1].isBlank() ? parts[0].trim() : parts[1].trim(), parts[2].trim()));
                } else if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    banks.add(new Bank(parts[0].trim(), parts[0].trim(), parts[1].trim()));
                }
            }
            return List.copyOf(banks);
        }
    }

    /**
     * ok (with the bank's journal number for a debit), or why not: ACCOUNT_NOT_FOUND, WRONG_CODE, CODE_EXPIRED,
     * INSUFFICIENT_FUNDS, REFUSED, GATEWAY_ERROR (nothing happened, try again) or NO_ANSWER (the bank was asked to take
     * the money but its answer never came: it may or may not have moved, so staff must check with the bank).
     */
    record Answer(boolean ok, String code, String message, String reference) {
        public static Answer ok(String message) {
            return new Answer(true, "OK", message, null);
        }

        public static Answer ok(String message, String reference) {
            return new Answer(true, "OK", message, reference);
        }

        public static Answer no(String code, String message) {
            return new Answer(false, code, message, null);
        }
    }

    /** True for the test version (the screens say so, and nothing real happens). */
    boolean testMode();

    /** The banks the customer can pay from. */
    List<Bank> banks();

    /** Registers the payment. Throws IllegalStateException (with a message for the customer) when the gateway refuses. */
    String start(String reference, BigDecimal amount, String description, String customerEmail);

    Answer requestCode(String gatewayTransactionId, String bankCode, String accountNumber);

    Answer debit(String gatewayTransactionId, String code);
}
