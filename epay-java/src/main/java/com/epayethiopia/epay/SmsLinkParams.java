package com.epayethiopia.epay;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.epayethiopia.epay.exception.EpayValidationException;

/**
 * The body of {@code POST /v1/transactions/sms-link}.
 *
 * <pre>{@code
 * SmsLinkParams params = SmsLinkParams.builder()
 *         .customerPhone("0911234567")
 *         .currencyCode("ETB")
 *         .amount("500.00")   // omit to let the customer enter the amount
 *         .build();
 * }</pre>
 */
public final class SmsLinkParams {

    private final String customerPhone;
    private final String currencyCode;
    private final String amount;

    private SmsLinkParams(Builder builder) {
        this.customerPhone = Validation.normalizePhone(builder.customerPhone);
        this.currencyCode = Validation.normalizeCurrency(builder.currencyCode);
        this.amount = builder.amount;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The phone number in {@code +251XXXXXXXXX} form. */
    public String customerPhone() {
        return customerPhone;
    }

    /** The uppercased ISO 4217 code. */
    public String currencyCode() {
        return currencyCode;
    }

    /** The fixed amount signed into the link, if one was set. */
    public Optional<String> amount() {
        return Optional.ofNullable(amount);
    }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerPhone", customerPhone);
        body.put("currencyCode", currencyCode);
        if (amount != null) {
            body.put("amount", amount);
        }
        return body;
    }

    @Override
    public String toString() {
        return "SmsLinkParams{currencyCode=" + currencyCode + ", amount=" + amount + "}";
    }

    /** Builds {@link SmsLinkParams}. {@code customerPhone} and {@code currencyCode} are required. */
    public static final class Builder {
        private String customerPhone;
        private String currencyCode;
        private String amount;

        private Builder() {}

        /** The Ethiopian mobile number to text the link to. Required. */
        public Builder customerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
            return this;
        }

        /** An ISO 4217 code such as {@code ETB}, case-insensitive. Required. */
        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        /** A fixed amount to pre-fill and sign into the link, as a decimal string. */
        public Builder amount(String amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /** A fixed amount to pre-fill and sign into the link. */
        public Builder amount(BigDecimal amount) {
            this.amount = Validation.formatAmount(amount);
            return this;
        }

        /** @throws EpayValidationException if a required field is missing or malformed */
        public SmsLinkParams build() {
            return new SmsLinkParams(this);
        }
    }
}
