package com.epayethiopia.epay.model;

/** A webhook event name. */
public enum EventType {
    PAYMENT_SUCCESS("payment.success"),
    PAYMENT_FAILED("payment.failed"),
    PAYMENT_CANCELLED("payment.cancelled"),
    PAYMENT_REFUNDING("payment.refunding"),
    PAYMENT_REFUNDED("payment.refunded"),
    PAYMENT_REVERSED("payment.reversed"),
    /**
     * An event this SDK version does not document yet. The raw name is still
     * available from {@link WebhookEvent#eventName()}.
     */
    UNKNOWN("unknown");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /** The wire value, such as {@code "payment.success"}. */
    public String value() {
        return value;
    }

    /** Looks up an event type by its wire value, returning {@link #UNKNOWN} when unrecognised. */
    public static EventType fromValue(String value) {
        for (EventType type : values()) {
            if (type != UNKNOWN && type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return value;
    }
}
