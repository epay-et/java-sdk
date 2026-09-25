package com.epayethiopia.epay;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

import com.epayethiopia.epay.exception.EpayValidationException;

/**
 * Validation and normalization for request values.
 *
 * <p>The client applies these automatically before sending a request, so a typo
 * fails immediately instead of costing a round trip. They are public so you can
 * validate input at your own boundary, such as a checkout form.
 */
public final class Validation {

    /** Largest amount the API accepts. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    /** Longest span the transaction list endpoint allows between {@code from} and {@code to}. */
    public static final int MAX_LIST_RANGE_DAYS = 90;

    /** Longest idempotency key the API accepts. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    // At most 9 integer digits, which is exactly the MAX_AMOUNT ceiling, so no
    // separate range comparison is needed.
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,9}(\\.\\d{1,2})?$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s()\\-.]");
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern PHONE_INTERNATIONAL = Pattern.compile("^251[79]\\d{8}$");
    private static final Pattern PHONE_LOCAL = Pattern.compile("^0[79]\\d{8}$");
    private static final Pattern PHONE_BARE = Pattern.compile("^[79]\\d{8}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private static final long MAX_MINOR_UNITS = 99_999_999_999L;

    private Validation() {}

    /**
     * Validates an amount string and pads it to exactly two decimal places, so
     * {@code "250"}, {@code "250.5"}, and {@code "250.50"} become {@code "250.00"},
     * {@code "250.50"}, and {@code "250.50"}.
     *
     * @throws EpayValidationException if the value is not a positive amount of at
     *     most 9 integer digits and 2 decimal places
     */
    public static String formatAmount(String value) {
        if (value == null) {
            throw new EpayValidationException("amount is required");
        }
        String candidate = value.strip();
        if (!AMOUNT.matcher(candidate).matches()) {
            throw new EpayValidationException(String.format(Locale.ROOT,
                    "amount must be a positive numeric string of at most %s, with at most 9 "
                            + "integer digits and 2 decimal places, received \"%s\"",
                    MAX_AMOUNT.toPlainString(), value));
        }

        int dot = candidate.indexOf('.');
        String whole = dot < 0 ? candidate : candidate.substring(0, dot);
        String fraction = dot < 0 ? "" : candidate.substring(dot + 1);

        // Reject zero without arithmetic: any 0(.00) form is invalid.
        if (whole.chars().allMatch(c -> c == '0') && fraction.chars().allMatch(c -> c == '0')) {
            throw new EpayValidationException(
                    "amount must be greater than 0, received \"" + value + "\"");
        }

        return whole + "." + (fraction + "00").substring(0, 2);
    }

    /**
     * Formats a {@link BigDecimal} amount with exactly two decimal places.
     *
     * <p>Values with more than two significant decimal places are rejected rather
     * than rounded: silently changing an amount is worse than failing.
     *
     * @throws EpayValidationException if the value is not a positive amount in range
     */
    public static String formatAmount(BigDecimal value) {
        if (value == null) {
            throw new EpayValidationException("amount is required");
        }
        if (value.signum() <= 0) {
            throw new EpayValidationException(
                    "amount must be greater than 0, received " + value.toPlainString());
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new EpayValidationException(
                    "amount must have at most 2 decimal places, received " + value.toPlainString());
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new EpayValidationException(String.format(Locale.ROOT,
                    "amount must not exceed %s, received %s",
                    MAX_AMOUNT.toPlainString(), value.toPlainString()));
        }
        return value.setScale(2).toPlainString();
    }

    /**
     * Renders an integer number of cents as the amount string the API expects,
     * exactly and without floating point: {@code 25000} becomes {@code "250.00"}.
     *
     * @throws EpayValidationException if the value is not positive or exceeds {@link #MAX_AMOUNT}
     */
    public static String amountFromMinorUnits(long cents) {
        if (cents <= 0) {
            throw new EpayValidationException(
                    "amount must be greater than 0, received " + cents + " cents");
        }
        if (cents > MAX_MINOR_UNITS) {
            throw new EpayValidationException(String.format(Locale.ROOT,
                    "amount must not exceed %s, received %d cents", MAX_AMOUNT.toPlainString(), cents));
        }
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
    }

    /**
     * Parses an amount back into an integer number of cents, for arithmetic or
     * storage in an integer column.
     *
     * @throws EpayValidationException if the value is not a valid amount
     */
    public static long toMinorUnits(String amount) {
        String formatted = formatAmount(amount);
        return Long.parseLong(formatted.replace(".", ""));
    }

    /**
     * Normalizes an Ethiopian mobile number to {@code +251XXXXXXXXX}.
     *
     * <p>Accepts local ({@code 0911234567}), bare national ({@code 911234567}), and
     * international ({@code 251911234567}, {@code +251911234567}) forms, with
     * spaces, dashes, dots, and parentheses anywhere. Both Ethio Telecom
     * ({@code 9…}) and Safaricom Ethiopia ({@code 7…}) ranges are recognised.
     *
     * @throws EpayValidationException if the value is not a recognised Ethiopian mobile number
     */
    public static String normalizePhone(String value) {
        if (value == null) {
            throw new EpayValidationException("customerPhone is required");
        }
        String cleaned = PHONE_SEPARATORS.matcher(value).replaceAll("");
        String digits = cleaned.startsWith("+") ? cleaned.substring(1) : cleaned;

        if (digits.isEmpty() || !DIGITS.matcher(digits).matches()) {
            throw new EpayValidationException(
                    "customerPhone must contain only digits and separators, received \"" + value + "\"");
        }

        String subscriber;
        if (PHONE_INTERNATIONAL.matcher(digits).matches()) {
            subscriber = digits.substring(3);
        } else if (PHONE_LOCAL.matcher(digits).matches()) {
            subscriber = digits.substring(1);
        } else if (PHONE_BARE.matcher(digits).matches()) {
            subscriber = digits;
        } else {
            throw new EpayValidationException(
                    "customerPhone must be an Ethiopian mobile number such as +251911234567 "
                            + "or 0911234567, received \"" + value + "\"");
        }

        return "+251" + subscriber;
    }

    /**
     * Validates a 3-letter ISO 4217 code and uppercases it.
     *
     * @throws EpayValidationException if the value is not three letters
     */
    public static String normalizeCurrency(String value) {
        return normalizeCurrency(value, "currencyCode");
    }

    static String normalizeCurrency(String value, String field) {
        String code = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(code).matches()) {
            throw new EpayValidationException(
                    field + " must be a 3-letter ISO 4217 code such as ETB, received \"" + value + "\"");
        }
        return code;
    }

    /** Validates a transaction reference is present and trims it. */
    static String requireReference(String value) {
        String reference = value == null ? "" : value.strip();
        if (reference.isEmpty()) {
            throw new EpayValidationException("reference must be a non-empty string");
        }
        return reference;
    }

    /** Masks a secret so it can be logged safely. */
    static String maskSecret(String value) {
        if (value == null || value.length() <= 12) {
            return "***";
        }
        return value.substring(0, 8) + "…" + value.substring(value.length() - 4);
    }
}
