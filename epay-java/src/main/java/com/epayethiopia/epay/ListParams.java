package com.epayethiopia.epay;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.epayethiopia.epay.exception.EpayValidationException;
import com.epayethiopia.epay.model.TransactionStatus;

/**
 * Query filters for {@code GET /v1/transactions}.
 *
 * <p>When both dates are set, the API rejects ranges longer than 90 days.
 */
public final class ListParams {

    private static final ListParams NONE = builder().build();

    private final String from;
    private final String to;
    private final String currency;
    private final TransactionStatus status;
    private final String cursor;

    private ListParams(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.currency = builder.currency == null
                ? null
                : Validation.normalizeCurrency(builder.currency, "currency");
        if (builder.status == TransactionStatus.UNKNOWN) {
            throw new EpayValidationException(
                    "status UNKNOWN is a placeholder for unrecognised responses and cannot be used as a filter");
        }
        this.status = builder.status;
        this.cursor = builder.cursor;

        LocalDate fromDate = asDate(from);
        LocalDate toDate = asDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new EpayValidationException(
                    "from (" + from + ") must not be later than to (" + to + ")");
        }
    }

    /** No filters: the most recent page. */
    public static ListParams none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> from() {
        return Optional.ofNullable(from);
    }

    public Optional<String> to() {
        return Optional.ofNullable(to);
    }

    public Optional<String> currency() {
        return Optional.ofNullable(currency);
    }

    public Optional<TransactionStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<String> cursor() {
        return Optional.ofNullable(cursor);
    }

    /** A copy with a different cursor and every filter kept. */
    ListParams withCursor(String nextCursor) {
        Builder builder = new Builder();
        builder.from = from;
        builder.to = to;
        builder.currency = currency;
        builder.status = status;
        builder.cursor = nextCursor;
        return builder.build();
    }

    Map<String, String> toQuery() {
        Map<String, String> query = new LinkedHashMap<>();
        if (from != null) {
            query.put("from", from);
        }
        if (to != null) {
            query.put("to", to);
        }
        if (currency != null) {
            query.put("currency", currency);
        }
        if (status != null) {
            query.put("status", status.value());
        }
        if (cursor != null) {
            query.put("cursor", cursor);
        }
        return query;
    }

    /** Parses a {@code YYYY-MM-DD} value, or returns null for any other form. */
    private static LocalDate asDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Builds {@link ListParams}. Every filter is optional. */
    public static final class Builder {
        private String from;
        private String to;
        private String currency;
        private TransactionStatus status;
        private String cursor;

        private Builder() {}

        /** Transactions created on or after this date. */
        public Builder from(LocalDate from) {
            this.from = from == null ? null : from.toString();
            return this;
        }

        /** Transactions created on or after this date, as {@code YYYY-MM-DD}. */
        public Builder from(String from) {
            this.from = requireNonBlank(from, "from");
            return this;
        }

        /** Transactions created on or before this date. */
        public Builder to(LocalDate to) {
            this.to = to == null ? null : to.toString();
            return this;
        }

        /** Transactions created on or before this date, as {@code YYYY-MM-DD}. */
        public Builder to(String to) {
            this.to = requireNonBlank(to, "to");
            return this;
        }

        /** A 3-letter ISO 4217 code, case-insensitive. */
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        /** Restricts the result to a single status. */
        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        /** The {@code nextCursor} from a previous page. Leave unset for page one. */
        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        /** @throws EpayValidationException if the range is inverted or a filter is malformed */
        public ListParams build() {
            return new ListParams(this);
        }

        private static String requireNonBlank(String value, String field) {
            if (value == null) {
                return null;
            }
            if (value.isBlank()) {
                throw new EpayValidationException(field + " must not be blank");
            }
            return value.strip();
        }
    }
}
