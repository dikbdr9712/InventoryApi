package com.api.inventory;

import com.api.inventory.service.payments.BankGatewayClient.Answer;
import com.api.inventory.service.payments.BankGatewayClient.Bank;
import com.api.inventory.service.payments.RmaBankGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real RMA connector against a pretend RMA gateway: the messages, their signatures (checked with DK/Phar's public
 * key, as RMA does), RMA's signed answers, the bank's journal number, and what happens when an answer is missing or
 * forged. No network, no Spring.
 */
class RmaBankGatewayClientTest {

    private KeyPair merchant;
    private KeyPair rma;
    private final List<Map<String, String>> received = new ArrayList<>();
    /** How the pretend RMA answers each message type; null = the connection breaks. */
    private Function<Map<String, String>, Map<String, String>> rmaAnswers;
    private RmaBankGatewayClient client;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchant = generator.generateKeyPair();
        rma = generator.generateKeyPair();
        rmaAnswers = this::normalRma;
        client = new RmaBankGatewayClient("https://uat.rma.example/BFSSecure/nvpapi", "BE10000123", "01",
                merchant.getPrivate(), rma.getPublic(), "1.0", Bank.parse("1010:BoB:Bank of Bhutan,1020:BNB:Bhutan National Bank"),
                this::post);
    }

    /** The pretend RMA: refuses unsigned or wrongly signed messages, signs what it sends back. */
    private Map<String, String> post(URI url, Map<String, String> fields) throws IOException {
        received.add(new LinkedHashMap<>(fields));
        assertTrue(RmaBankGatewayClient.verify(fields, fields.get("bfs_checkSum"), merchant.getPublic()),
                "every message is signed with DK/Phar's key");
        Map<String, String> answer = rmaAnswers.apply(fields);
        if (answer == null) {
            throw new IOException("connection reset");
        }
        Map<String, String> signed = new LinkedHashMap<>(answer);
        try {
            signed.put("bfs_checkSum", RmaBankGatewayClient.sign(answer, rma.getPrivate()));
        } catch (Exception e) {
            throw new IOException(e);
        }
        return signed;
    }

    private Map<String, String> normalRma(Map<String, String> m) {
        return switch (m.get("bfs_msgType")) {
            case "AR" -> Map.of("bfs_msgType", "AC", "bfs_responseCode", "00", "bfs_bfsTxnId", "RMA7788990011", "bfs_orderNo", m.get("bfs_orderNo"));
            case "AE" -> m.get("bfs_remitterAccNo").endsWith("0000")
                    ? Map.of("bfs_msgType", "EC", "bfs_responseCode", "53", "bfs_responseDesc", "Invalid remitter account")
                    : Map.of("bfs_msgType", "EC", "bfs_responseCode", "00", "bfs_bfsTxnId", m.get("bfs_bfsTxnId"));
            case "DR" -> "246810".equals(m.get("bfs_remitterOtp"))
                    ? Map.of("bfs_msgType", "AC", "bfs_debitAuthCode", "00", "bfs_debitAuthNo", "BOB240611223344", "bfs_bfsTxnId", m.get("bfs_bfsTxnId"))
                    : Map.of("bfs_msgType", "AC", "bfs_debitAuthCode", "55", "bfs_responseDesc", "Invalid OTP");
            default -> fail("unexpected message " + m.get("bfs_msgType"));
        };
    }

    @Test
    void aPaymentGoesThroughWithTheBanksJournalNumber() {
        assertFalse(client.testMode());
        String txn = client.start("PI-AB12CD34EF56", new BigDecimal("1350.5"), "DK/Phar order 1042", "karma@example.bt");
        assertEquals("RMA7788990011", txn);
        Map<String, String> ar = received.get(0);
        assertEquals("AR", ar.get("bfs_msgType"));
        assertEquals("BE10000123", ar.get("bfs_benfId"));
        assertEquals("PI-AB12CD34EF56", ar.get("bfs_orderNo"));
        assertEquals("1350.50", ar.get("bfs_txnAmount"), "two decimals");
        assertEquals("BTN", ar.get("bfs_txnCurrency"));
        assertTrue(ar.get("bfs_benfTxnTime").matches("\\d{14}"));

        assertTrue(client.requestCode(txn, "1010", "110022334321").ok());
        Map<String, String> ae = received.get(1);
        assertEquals("1010", ae.get("bfs_remitterBankId"));
        assertEquals(txn, ae.get("bfs_bfsTxnId"));

        Answer paid = client.debit(txn, "246810");
        assertTrue(paid.ok());
        assertEquals("BOB240611223344", paid.reference(), "the bank's journal number");
        assertEquals("246810", received.get(2).get("bfs_remitterOtp"));
    }

    @Test
    void theBanksRefusalsAreExplained() {
        String txn = client.start("PI-1", new BigDecimal("10"), "x", "a@b.bt");
        Answer unknown = client.requestCode(txn, "1020", "5566770000");
        assertEquals("ACCOUNT_NOT_FOUND", unknown.code());
        assertTrue(unknown.message().contains("Invalid remitter account"), unknown.message());
        assertEquals("WRONG_CODE", client.debit(txn, "111111").code());
    }

    @Test
    void aMissingOrForgedAnswerToTheDebitIsNeverTreatedAsFailed() {
        String txn = client.start("PI-2", new BigDecimal("10"), "x", "a@b.bt");
        client.requestCode(txn, "1010", "110022334321");

        rmaAnswers = m -> null; // the connection breaks after the debit was sent
        assertEquals("NO_ANSWER", client.debit(txn, "246810").code(), "money may have moved: staff must check");

        rmaAnswers = m -> Map.of("bfs_msgType", "AC", "bfs_debitAuthCode", "00", "bfs_debitAuthNo", "FORGED");
        RmaBankGatewayClient forgedRma = new RmaBankGatewayClient("https://uat.rma.example/x", "BE10000123", "01",
                merchant.getPrivate(), merchant.getPublic(), // expects RMA's key, gets answers signed by another
                "1.0", Bank.parse("1010:BoB:Bank of Bhutan"), this::post);
        assertEquals("NO_ANSWER", forgedRma.debit(txn, "246810").code(), "an answer that fails its signature check is not believed");
        assertEquals("GATEWAY_ERROR", forgedRma.requestCode(txn, "1010", "110022334321").code());

        rmaAnswers = m -> null;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client.start("PI-3", BigDecimal.ONE, "x", "a@b.bt"));
        assertTrue(e.getMessage().contains("Nothing was taken"), e.getMessage());
    }

    @Test
    void theSignatureCoversTheSortedFieldValues() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bfs_msgType", "AE");
        fields.put("bfs_benfId", "BE1");
        fields.put("bfs_bfsTxnId", "T9");
        fields.put("bfs_checkSum", "IGNORED");
        assertEquals("BE1|T9|AE", RmaBankGatewayClient.sourceString(fields), "sorted by field name, checksum left out");
        assertEquals(Map.of("bfs_responseCode", "00", "bfs_responseDesc", "Approved & done"),
                RmaBankGatewayClient.parseAnswer("bfs_responseCode=00&bfs_responseDesc=Approved+%26+done"));
    }

    @Test
    void settingsMustBeComplete() {
        assertThrows(IllegalStateException.class, () -> new RmaBankGatewayClient("http://not-https.example", "BE1", "01",
                merchant.getPrivate(), null, "1.0", Bank.parse("1010:BoB:Bank of Bhutan"), this::post));
        assertThrows(IllegalStateException.class, () -> new RmaBankGatewayClient("https://x.example", " ", "01",
                merchant.getPrivate(), null, "1.0", Bank.parse("1010:BoB:Bank of Bhutan"), this::post));
    }
}
