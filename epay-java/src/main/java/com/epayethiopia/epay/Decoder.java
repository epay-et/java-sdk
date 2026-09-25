package com.epayethiopia.epay;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.epayethiopia.epay.model.Customer;
import com.epayethiopia.epay.model.EventType;
import com.epayethiopia.epay.model.InitializedPayment;
import com.epayethiopia.epay.model.Mode;
import com.epayethiopia.epay.model.Provider;
import com.epayethiopia.epay.model.ProviderItem;
import com.epayethiopia.epay.model.Timeline;
import com.epayethiopia.epay.model.TimelineEvent;
import com.epayethiopia.epay.model.Transaction;
import com.epayethiopia.epay.model.TransactionStatus;
import com.epayethiopia.epay.model.VerifiedPayment;
import com.epayethiopia.epay.model.WebhookEvent;

/**
 * Maps parsed JSON onto the model records.
 *
 * <p>Decoding is lenient by design: a missing field becomes null, and a value in
 * an unexpected shape is dropped rather than failing the whole response. A
 * payment that verified must never be rejected by the SDK because the API added
 * or reformatted an unrelated field.
 */
final class Decoder {
    private Decoder() {}

    static InitializedPayment initializedPayment(Map<String, Object> json) {
        return new InitializedPayment(
                string(json, "reference"),
                string(json, "checkoutUrl"),
                string(json, "status"),
                instant(json, "expiresAt"));
    }

    static Transaction transaction(Map<String, Object> json) {
        return new Transaction(
                string(json, "reference"),
                string(json, "merchantReference"),
                status(json),
                decimal(json, "amount"),
                string(json, "currencyCode"),
                instant(json, "paidAt"),
                instant(json, "createdAt"));
    }

    static VerifiedPayment verifiedPayment(Map<String, Object> json) {
        return new VerifiedPayment(
                string(json, "reference"),
                string(json, "merchantReference"),
                status(json),
                decimal(json, "amount"),
                decimal(json, "serviceFee"),
                string(json, "currencyCode"),
                string(json, "paymentMethod"),
                customer(json),
                instant(json, "paidAt"),
                instant(json, "createdAt"));
    }

    static Timeline timeline(Map<String, Object> json) {
        List<TimelineEvent> events = new ArrayList<>();
        for (Map<String, Object> item : objects(json.get("events"))) {
            events.add(new TimelineEvent(
                    string(item, "eventType"),
                    instant(item, "occurredAt"),
                    string(item, "errorCode"),
                    string(item, "errorMessage")));
        }
        return new Timeline(string(json, "reference"), events);
    }

    static List<ProviderItem> providerItems(Map<String, Object> json) {
        List<ProviderItem> items = new ArrayList<>();
        for (Map<String, Object> item : objects(json.get("data"))) {
            items.add(new ProviderItem(
                    string(item, "providerCode"), string(item, "providerName"), string(item, "flowCode")));
        }
        return List.copyOf(items);
    }

    static List<Provider> providers(Map<String, Object> json) {
        List<Provider> providers = new ArrayList<>();
        for (Map<String, Object> item : objects(json.get("data"))) {
            providers.add(new Provider(
                    string(item, "providerCode"),
                    string(item, "providerName"),
                    string(item, "providerCategory"),
                    string(item, "flowCode"),
                    Boolean.TRUE.equals(item.get("isEnabled"))));
        }
        return List.copyOf(providers);
    }

    static List<Transaction> transactions(Object data) {
        List<Transaction> transactions = new ArrayList<>();
        for (Map<String, Object> item : objects(data)) {
            transactions.add(transaction(item));
        }
        return List.copyOf(transactions);
    }

    static WebhookEvent webhookEvent(Map<String, Object> json) {
        String eventName = string(json, "event");
        return new WebhookEvent(
                EventType.fromValue(eventName),
                eventName,
                Mode.fromValue(string(json, "mode")).orElse(null),
                string(json, "reference"),
                string(json, "merchantReference"),
                decimal(json, "amount"),
                decimal(json, "serviceFee"),
                string(json, "currency"),
                status(json),
                string(json, "paymentMethod"),
                customer(json),
                instant(json, "paidAt"),
                instant(json, "createdAt"),
                json);
    }

    // ---------------------------------------------------------------- fields

    private static Customer customer(Map<String, Object> json) {
        Map<String, Object> customer = object(json.get("customer"));
        if (customer == null) {
            return new Customer(null, null, null);
        }
        return new Customer(string(customer, "name"), string(customer, "email"), string(customer, "phone"));
    }

    private static TransactionStatus status(Map<String, Object> json) {
        String value = string(json, "status");
        return value == null ? null : TransactionStatus.fromValue(value);
    }

    static String string(Map<String, Object> json, String key) {
        return json.get(key) instanceof String value ? value : null;
    }

    static boolean bool(Map<String, Object> json, String key) {
        return Boolean.TRUE.equals(json.get(key));
    }

    /** Amounts arrive as strings; a bare JSON number is accepted too. */
    private static BigDecimal decimal(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Accepts {@code 2026-08-13T11:30:00.000Z}, any offset, or a local time taken as UTC. */
    static Instant instant(Map<String, Object> json, String key) {
        if (!(json.get(key) instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notInstant) {
            // Try the wider forms below.
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException notOffset) {
            // Fall through.
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static List<Map<String, Object>> objects(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = object(item);
                if (map != null) {
                    result.add(map);
                }
            }
        }
        return result;
    }
}
