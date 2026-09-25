package com.epayethiopia.epay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.epayethiopia.epay.exception.EpayConfigException;
import com.epayethiopia.epay.exception.EpayValidationException;
import com.epayethiopia.epay.exception.EpayWebhookSignatureException;
import com.epayethiopia.epay.model.EventType;
import com.epayethiopia.epay.model.Mode;
import com.epayethiopia.epay.model.TransactionStatus;
import com.epayethiopia.epay.model.WebhookEvent;

class WebhooksTest {

    static final String SECRET = "whsec_test_secret";
    static final String PAYLOAD = "{\"event\":\"payment.success\",\"reference\":\"P1a2b3c4d20260813\"}";
    // Computed independently with Python's hmac module.
    static final String SIGNATURE = "9cccb2152eec70d818ea085c46725089f4d6c301c8996968976611079788c75f";

    final Webhooks webhooks = Webhooks.withSecret(SECRET);

    @Test
    void signsLikeEveryOtherImplementation() {
        assertThat(webhooks.sign(PAYLOAD)).isEqualTo(SIGNATURE);
        assertThat(webhooks.sign("ብር {\"a\":1}"))
                .isEqualTo("c0927afa24076d63fec9b7446cfaf062a8df721a39b03263f4c12b47cedf00a1");
    }

    @ParameterizedTest
    @ValueSource(strings = {SIGNATURE, "sha256=" + SIGNATURE, "SHA256=" + SIGNATURE, "  " + SIGNATURE + "  "})
    void acceptsEveryHeaderForm(String header) {
        assertThat(webhooks.verify(PAYLOAD, header)).isTrue();
        assertThat(webhooks.verify(PAYLOAD, header.toUpperCase(java.util.Locale.ROOT).replace("SHA256=", "sha256=")))
                .isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "sha256=", "nothex", "abc", "00"})
    void rejectsMalformedHeaders(String header) {
        assertThat(webhooks.verify(PAYLOAD, header)).isFalse();
        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, header))
                .isInstanceOf(EpayWebhookSignatureException.class);
    }

    @Test
    void rejectsATamperedBody() {
        String tampered = PAYLOAD.replace("success", "failed");
        assertThat(webhooks.verify(tampered, SIGNATURE)).isFalse();
        assertThatThrownBy(() -> webhooks.constructEvent(tampered, SIGNATURE))
                .isInstanceOf(EpayWebhookSignatureException.class)
                .hasMessageContaining("raw request body");
    }

    @Test
    void rejectsReformattedJson() {
        String reformatted = "{\"event\": \"payment.success\", \"reference\": \"P1a2b3c4d20260813\"}";
        assertThat(webhooks.verify(reformatted, SIGNATURE)).isFalse();
    }

    @Test
    void decodesAVerifiedEvent() {
        String body = "{\"event\":\"payment.success\",\"mode\":\"sandbox\",\"reference\":\"P1\","
                + "\"merchantReference\":\"order_123\",\"amount\":\"250.00\",\"serviceFee\":\"5.00\","
                + "\"currency\":\"ETB\",\"status\":\"completed\",\"paymentMethod\":\"telebirr\","
                + "\"customer\":{\"name\":\"Abebe\",\"email\":\"a@example.com\",\"phone\":\"+251911234567\"},"
                + "\"paidAt\":\"2026-08-13T11:47:00.000Z\",\"createdAt\":\"2026-08-13T11:30:00.000Z\","
                + "\"brandNew\":\"field\"}";
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);

        WebhookEvent event = webhooks.constructEvent(raw, "sha256=" + webhooks.sign(raw));

        assertThat(event.type()).isEqualTo(EventType.PAYMENT_SUCCESS);
        assertThat(event.isKnown()).isTrue();
        assertThat(event.mode()).isEqualTo(Mode.SANDBOX);
        assertThat(event.amount()).isEqualTo(new BigDecimal("250.00"));
        assertThat(event.currency()).isEqualTo("ETB");
        assertThat(event.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(event.customer().email()).isEqualTo("a@example.com");
        assertThat(event.paidAt()).isEqualTo(Instant.parse("2026-08-13T11:47:00Z"));
        assertThat(event.raw()).containsEntry("brandNew", "field");
    }

    @Test
    void keepsUnknownEventNames() {
        String body = "{\"event\":\"payment.disputed\",\"reference\":\"P1\"}";
        WebhookEvent event = webhooks.constructEvent(body, webhooks.sign(body));
        assertThat(event.type()).isEqualTo(EventType.UNKNOWN);
        assertThat(event.eventName()).isEqualTo("payment.disputed");
        assertThat(event.isKnown()).isFalse();
    }

    @Test
    void rejectsSignedBodiesThatAreNotObjects() {
        assertThatThrownBy(() -> webhooks.constructEvent("[1]", webhooks.sign("[1]")))
                .isInstanceOf(EpayValidationException.class).hasMessageContaining("JSON object");
        assertThatThrownBy(() -> webhooks.constructEvent("not json", webhooks.sign("not json")))
                .isInstanceOf(EpayValidationException.class).hasMessageContaining("valid JSON");
    }

    @Test
    void requiresASecret() {
        Webhooks unconfigured = new Webhooks(null);
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.verify(PAYLOAD, SIGNATURE))
                .isInstanceOf(EpayConfigException.class)
                .hasMessageContaining("EPAY_WEBHOOK_SECRET");
        assertThatThrownBy(() -> Webhooks.withSecret("")).isInstanceOf(EpayConfigException.class);
    }

    @Test
    void theClientWiresItsSecret() {
        Epay epay = Epay.builder().apiKey("sk_test_abcdefghijklmnop").webhookSecret(SECRET)
                .environment(name -> null).build();
        assertThat(epay.webhooks().verify(PAYLOAD, SIGNATURE)).isTrue();
    }

    @Test
    void documentsEveryEventType() {
        for (String name : Webhooks.EVENT_TYPES) {
            assertThat(EventType.fromValue(name)).isNotEqualTo(EventType.UNKNOWN);
        }
        assertThat(Webhooks.EVENT_TYPES).hasSize(EventType.values().length - 1);
    }
}
