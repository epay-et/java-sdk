package com.epayethiopia.epay.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Parses a {@code Retry-After} header. Not part of the public API. */
public final class RetryAfter {
    private RetryAfter() {}

    /**
     * Reads either form the header allows: delta-seconds, or an HTTP-date.
     * A date in the past yields zero.
     */
    public static Optional<Duration> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.strip();
        try {
            double seconds = Double.parseDouble(trimmed);
            if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(Math.max(0, Math.round(seconds * 1000))));
        } catch (NumberFormatException notSeconds) {
            // Fall through to the HTTP-date form.
        }
        try {
            Instant at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration wait = Duration.between(Instant.now(), at);
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }
}
