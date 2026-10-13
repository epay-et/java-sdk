package com.epayethiopia.epay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * A verified webhook delivery.
 *
 * <p>Note the currency field is {@code currency} here, not {@code currencyCode} as
 * on the transaction endpoints.
 *
 * @param type the event, or {@link EventType#UNKNOWN} for one this SDK version does not document
 * @param eventName the raw event name, such as {@code payment.success}
 * @param mode live or sandbox, or null when absent or unrecognised
 * @param reference the ePay reference; deduplicate deliveries on this
 * @param merchantReference your own reference, or null
 * @param amount the amount, or null
 * @param serviceFee the fee, or null
 * @param currency an ISO 4217 code, or null
 * @param status the transaction's status after this event
 * @param paymentMethod the provider the customer paid with, or null
 * @param customer the customer's details
 * @param paidAt when the payment completed; null for every non-success event
 * @param createdAt when the transaction was created
 * @param customerPhone the customer's phone number, on direct payment events; otherwise null
 * @param pspCode the provider charged, on direct payment events; otherwise null
 * @param occurredAt when the event occurred, or null
 * @param reason why the payment failed, on {@code payment.failed}; otherwise null
 * @param raw the full decoded payload, including any field this SDK version does not model
 */
public record WebhookEvent(
        EventType type,
        String eventName,
        Mode mode,
        String reference,
        String merchantReference,
        BigDecimal amount,
        BigDecimal serviceFee,
        String currency,
        TransactionStatus status,
        String paymentMethod,
        Customer customer,
        Instant paidAt,
        Instant createdAt,
        String customerPhone,
        String pspCode,
        Instant occurredAt,
        String reason,
        Map<String, Object> raw) {

    public WebhookEvent {
        raw = raw == null ? Map.of() : raw;
    }

    /** Whether the event name is one this SDK version documents. */
    public boolean isKnown() {
        return type != EventType.UNKNOWN;
    }
}
