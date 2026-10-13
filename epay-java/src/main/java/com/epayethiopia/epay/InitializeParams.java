package com.epayethiopia.epay;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.epayethiopia.epay.exception.EpayValidationException;

/**
 * The body of {@code POST /v1/transactions/initialize}.
 *
 * <pre>{@code
 * InitializeParams params = InitializeParams.builder()
 *         .amount("250.00")
 *         .currencyCode("ETB")
 *         .customerPhone("0911234567")
 *         .merchantReference("order_123")
 *         .idempotencyKey("order_123")
 *         .build();
 * }</pre>
 *
 * <p>{@link Builder#build()} validates and normalizes every field, so an instance
 * is always sendable: amounts are padded to two decimal places, currencies
 * uppercased, and phone numbers rewritten to {@code +251XXXXXXXXX}.
 */
public final class InitializeParams {

    private final String amount;
    private final String currencyCode;
    private final String customerPhone;
    private final String merchantReference;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String returnUrl;
    private final String callbackUrl;
    private final String idempotencyKey;

    private InitializeParams(Builder builder) {
        if (builder.amount == null) {
            throw new EpayValidationException("amount is required");
        }
        this.amount = builder.amount;
        this.currencyCode = Validation.normalizeCurrency(builder.currencyCode);
        this.customerPhone = Validation.normalizePhone(builder.customerPhone);
        this.merchantReference = builder.merchantReference;
        this.email = builder.email;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.returnUrl = builder.returnUrl;
        this.callbackUrl = builder.callbackUrl;

        this.idempotencyKey = Validation.checkIdempotencyKey(builder.idempotencyKey);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The amount, always with exactly two decimal places. */
    public String amount() {
        return amount;
    }

    /** The uppercased ISO 4217 code. */
    public String currencyCode() {
        return currencyCode;
    }

    /** The phone number in {@code +251XXXXXXXXX} form. */
    public String customerPhone() {
        return customerPhone;
    }

    public Optional<String> merchantReference() {
        return Optional.ofNullable(merchantReference);
    }

    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    public Optional<String> firstName() {
        return Optional.ofNullable(firstName);
    }

    public Optional<String> lastName() {
        return Optional.ofNullable(lastName);
    }

    public Optional<String> returnUrl() {
        return Optional.ofNullable(returnUrl);
    }

    public Optional<String> callbackUrl() {
        return Optional.ofNullable(callbackUrl);
    }

    /** The caller's idempotency key, if one was set. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    /**
     * The request body. Optional fields are only included when set, so the API
     * applies its own defaults rather than receiving explicit nulls.
     */
    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currencyCode", currencyCode);
        body.put("customerPhone", customerPhone);
        putIfSet(body, "merchantReference", merchantReference);
        putIfSet(body, "email", email);
        putIfSet(body, "firstName", firstName);
        putIfSet(body, "lastName", lastName);
        putIfSet(body, "returnUrl", returnUrl);
        putIfSet(body, "callbackUrl", callbackUrl);
        return body;
    }

    private static void putIfSet(Map<String, Object> body, String key, String value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "InitializeParams{amount=" + amount + ", currencyCode=" + currencyCode
                + ", merchantReference=" + merchantReference + "}";
    }

    /** Builds {@link InitializeParams}. {@code amount}, {@code currencyCode}, and {@code customerPhone} are required. */
    public static final class Builder {
        private String amount;
        private String currencyCode;
        private String customerPhone;
        private String merchantReference;
        private String email;
        private String firstName;
        private String lastName;
        private String returnUrl;
        private String callbackUrl;
        private String idempotencyKey;

        private Builder() {}

        /**
         * The amount as a decimal string, such as {@code "250.00"} or {@code "250"}.
         *
         * @throws EpayValidationException if it is not a positive amount of at most
         *     9 integer digits and 2 decimal places
         */
        public Builder amount(String amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /**
         * The amount as a {@link BigDecimal}. More than two significant decimal
         * places is rejected rather than rounded.
         *
         * @throws EpayValidationException if it is not a positive amount in range
         */
        public Builder amount(BigDecimal amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /**
         * The amount as an integer number of cents: {@code 25000} is {@code 250.00}.
         *
         * @throws EpayValidationException if it is not positive or is out of range
         */
        public Builder amountMinorUnits(long cents) {
            this.amount = Validation.amountFromMinorUnits(cents);
            return this;
        }

        /** An ISO 4217 code such as {@code ETB}, case-insensitive. Required. */
        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        /** An Ethiopian mobile number such as {@code +251911234567} or {@code 0911234567}. Required. */
        public Builder customerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
            return this;
        }

        /** Your own unique reference, such as an order id. */
        public Builder merchantReference(String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        /** Where to send the customer after payment, success or failure. */
        public Builder returnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        /** A per-transaction webhook URL, overriding the one set in the dashboard. */
        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        /**
         * Up to 255 characters. Resending the same key returns the original
         * response instead of creating a second transaction.
         *
         * <p>When unset the client generates a fresh key per call, which makes its
         * internal retries safe but does not deduplicate across separate calls.
         * Pass your order id to get that guarantee.
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * @throws EpayValidationException if a required field is missing or any
         *     field fails validation
         */
        public InitializeParams build() {
            return new InitializeParams(this);
        }
    }
}
