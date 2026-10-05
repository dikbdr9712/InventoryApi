package com.api.inventory.service.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TEST MODE for paying from a bank account (app.payments.bank.mode=test). Nothing real happens:
 * no bank is contacted, no money moves and no text message is sent. It answers like the gateway would, so the
 * screens and the order flow can be tried:
 *
 *   the code is always 123456
 *   an account number ending in 0000  -> "account not found"
 *   an account number ending in 9999  -> the bank refuses: not enough money
 *   an account number ending in 5555  -> the bank's answer to the debit never comes (staff must check)
 *   a successful payment gets a pretend journal number starting with TJ
 *
 * It refuses to start on the live site (the "prod" profile), unless app.payments.bank.test-on-live-site=true:
 * only for a demo copy of the shop on the internet, where everyone sees "TEST MODE: no real money".
 */
@Component
@ConditionalOnProperty(name = "app.payments.bank.mode", havingValue = "test")
public class TestBankGatewayClient implements BankGatewayClient {

    public static final String TEST_CODE = "123456";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final List<Bank> banks;
    /** gateway transaction -> last 4 digits of the account (only to act out the special accounts above) */
    private final Map<String, String> lastDigits = new ConcurrentHashMap<>();

    public TestBankGatewayClient(Environment environment, @Value("${app.payments.bank.banks}") String bankList,
                                 @Value("${app.payments.bank.test-on-live-site:false}") boolean demoSite) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            if (!demoSite) {
                throw new IllegalStateException("Test bank payments cannot run on the live site. Set app.payments.bank.mode=off or rma"
                        + " (or app.payments.bank.test-on-live-site=true for a demo copy that takes no real money).");
            }
            org.slf4j.LoggerFactory.getLogger(TestBankGatewayClient.class)
                    .warn("TEST MODE bank payments on a live (prod) site: a demo copy, no real money is taken.");
        }
        this.banks = Bank.parse(bankList);
    }

    @Override
    public boolean testMode() {
        return true;
    }

    @Override
    public List<Bank> banks() {
        return banks;
    }

    @Override
    public String start(String reference, BigDecimal amount, String description, String customerEmail) {
        byte[] b = new byte[5];
        RANDOM.nextBytes(b);
        return "TEST-" + HexFormat.of().withUpperCase().formatHex(b);
    }

    @Override
    public Answer requestCode(String gatewayTransactionId, String bankCode, String accountNumber) {
        if (accountNumber.endsWith("0000")) {
            return Answer.no("ACCOUNT_NOT_FOUND", "The bank does not know this account number. Check it and try again.");
        }
        lastDigits.put(gatewayTransactionId, accountNumber.substring(accountNumber.length() - 4));
        return Answer.ok("Test mode: no text message is sent. The code is " + TEST_CODE + ".");
    }

    @Override
    public Answer debit(String gatewayTransactionId, String code) {
        if (!TEST_CODE.equals(code)) {
            return Answer.no("WRONG_CODE", "That code is not right.");
        }
        String last4 = lastDigits.get(gatewayTransactionId);
        if ("9999".equals(last4)) {
            return Answer.no("INSUFFICIENT_FUNDS", "The bank refused the payment: there is not enough money in the account.");
        }
        if ("5555".equals(last4)) {
            return Answer.no("NO_ANSWER", "The bank did not answer.");
        }
        lastDigits.remove(gatewayTransactionId);
        return Answer.ok("Paid (test mode, no money moved).", "TJ" + (1_000_000_000L + RANDOM.nextLong(8_999_999_999L)));
    }
}
