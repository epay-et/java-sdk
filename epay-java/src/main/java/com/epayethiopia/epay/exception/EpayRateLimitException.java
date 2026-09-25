package com.epayethiopia.epay.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.epayethiopia.epay.internal.RetryAfter;

/**
 * {@code 429} — the 100 requests / 60s per-key budget is exhausted.
 *
 * <p>The SDK already retries these, so receiving one means the retry budget was
 * also exhausted.
 */
public class EpayRateLimitException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayRateLimitException(
            String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }

    /** How long to wait before retrying, from the {@code Retry-After} header. */
    public Optional<Duration> retryAfter() {
        return header("Retry-After").flatMap(RetryAfter::parse);
    }
}
