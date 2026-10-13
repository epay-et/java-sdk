package com.epayethiopia.epay;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.epayethiopia.epay.exception.EpayValidationException;

/**
 * The body of {@code POST /v1/transactions/direct}.
 *
 * <pre>{@code
 * DirectPaymentParams params = DirectPaymentParams.builder()
 *         .amount("250.00")
 *         .currencyCode("ETB")
 *         .customerPhone("0911234567")
 *         .pspCode("TELEBIRR")
 *         .merchantReference("order_456")
 *         .idempotencyKey("order_456")
 *         .build();
 * }</pre>
 *
 * <p>{@link Builder#build()} validates and normalizes every field, with the same
 * rules as {@link InitializeParams}.
 */
public final class DirectPaymentParams {

    private final String amount;
    private final String currencyCode;
    private final String customerPhone;
    private final String pspCode;
    private final String flowCode;
    private final String merchantReference;
    private final String callbackUrl;
    private final String idempotencyKey;

    private DirectPaymentParams(Builder builder) {
        if (builder.amount == null) {
            throw new EpayValidationException("amount is required");
        }
        this.amount = builder.amount;
        this.currencyCode = Validation.normalizeCurrency(builder.currencyCode);
        this.customerPhone = Validation.normalizePhone(builder.customerPhone);
        this.pspCode = requireCode(builder.pspCode, "pspCode");
        this.flowCode = builder.flowCode == null ? null : requireCode(builder.flowCode, "flowCode");
        this.merchantReference = builder.merchantReference;
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

    /** The provider to charge through, such as {@code TELEBIRR}. */
    public String pspCode() {
        return pspCode;
    }

    public Optional<String> flowCode() {
        return Optional.ofNullable(flowCode);
    }

    public Optional<String> merchantReference() {
        return Optional.ofNullable(merchantReference);
    }

    public Optional<String> callbackUrl() {
        return Optional.ofNullable(callbackUrl);
    }

    /** The caller's idempotency key, if one was set. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    /** The request body, with optional fields included only when set. */
    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currencyCode", currencyCode);
        body.put("customerPhone", customerPhone);
        body.put("pspCode", pspCode);
        if (flowCode != null) {
            body.put("flowCode", flowCode);
        }
        if (merchantReference != null) {
            body.put("merchantReference", merchantReference);
        }
        if (callbackUrl != null) {
            body.put("callbackUrl", callbackUrl);
        }
        return body;
    }

    @Override
    public String toString() {
        return "DirectPaymentParams{amount=" + amount + ", currencyCode=" + currencyCode
                + ", pspCode=" + pspCode + ", merchantReference=" + merchantReference + "}";
    }

    private static String requireCode(String value, String field) {
        String code = value == null ? "" : value.strip();
        if (code.isEmpty()) {
            throw new EpayValidationException(field + " must be a non-empty string");
        }
        return code;
    }

    /**
     * Builds {@link DirectPaymentParams}. {@code amount}, {@code currencyCode},
     * {@code customerPhone}, and {@code pspCode} are required.
     */
    public static final class Builder {
        private String amount;
        private String currencyCode;
        private String customerPhone;
        private String pspCode;
        private String flowCode;
        private String merchantReference;
        private String callbackUrl;
        private String idempotencyKey;

        private Builder() {}

        /** The amount as a decimal string, such as {@code "250.00"}. */
        public Builder amount(String amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /** The amount as a {@link BigDecimal}; more than two decimal places is rejected. */
        public Builder amount(BigDecimal amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /** The amount as an integer number of cents: {@code 25000} is {@code 250.00}. */
        public Builder amountMinorUnits(long cents) {
            this.amount = Validation.amountFromMinorUnits(cents);
            return this;
        }

        /** An ISO 4217 code such as {@code ETB}, case-insensitive. Required. */
        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        /** The customer's mobile number, registered with the provider. Required. */
        public Builder customerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
            return this;
        }

        /**
         * The provider to charge through, such as {@code TELEBIRR} or {@code AWASH}.
         * Required. See {@link PaymentProviders#listForDirectPayment()}.
         */
        public Builder pspCode(String pspCode) {
            this.pspCode = pspCode;
            return this;
        }

        /** Overrides the provider's default flow, such as {@code USSD} or {@code SMS_OTP}. */
        public Builder flowCode(String flowCode) {
            this.flowCode = flowCode;
            return this;
        }

        /** Your own unique reference, such as an order id. */
        public Builder merchantReference(String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        /** A per-transaction webhook URL, overriding the one set in the dashboard. */
        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        /**
         * Up to 255 characters. When unset the client generates a fresh key per
         * call, which makes its internal retries safe but does not deduplicate
         * across separate calls. Pass your order id so a double-submitted form
         * cannot charge twice.
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /** @throws EpayValidationException if a required field is missing or any field fails validation */
        public DirectPaymentParams build() {
            return new DirectPaymentParams(this);
        }
    }
}
