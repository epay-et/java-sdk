package com.epayethiopia.epay.model;

/** A lifecycle state a transaction can be in. */
public enum TransactionStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    REVERSED("reversed"),
    REFUNDING("refunding"),
    REFUNDED("refunded"),
    /**
     * A status this SDK version does not recognise yet. Responses decode to
     * this rather than failing, so a new API status cannot break your
     * integration. It is never sent to the API.
     */
    UNKNOWN("unknown");

    private final String value;

    TransactionStatus(String value) {
        this.value = value;
    }

    /** The wire value, such as {@code "completed"}. */
    public String value() {
        return value;
    }

    /** Whether a transaction in this status can still be cancelled. */
    public boolean isCancellable() {
        return this == PENDING || this == PROCESSING;
    }

    /** Looks up a status by its wire value, returning {@link #UNKNOWN} when unrecognised. */
    public static TransactionStatus fromValue(String value) {
        for (TransactionStatus status : values()) {
            if (status != UNKNOWN && status.value.equals(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return value;
    }
}
