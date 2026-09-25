package com.epayethiopia.epay;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.epayethiopia.epay.exception.EpayApiException;
import com.epayethiopia.epay.exception.EpayConnectionException;
import com.epayethiopia.epay.exception.EpayException;
import com.epayethiopia.epay.exception.EpayTimeoutException;
import com.epayethiopia.epay.http.EpayHttpClient;
import com.epayethiopia.epay.http.EpayHttpRequest;
import com.epayethiopia.epay.http.EpayHttpResponse;
import com.epayethiopia.epay.internal.Json;
import com.epayethiopia.epay.internal.RetryAfter;

/** URL building, retries with backoff, and error mapping. Thread-safe. */
final class Transport {

    private static final long BACKOFF_BASE_MS = 500;
    private static final long BACKOFF_MAX_MS = 8_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Pauses between attempts. Replaced in tests so retries do not sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final Map<String, String> defaultHeaders;
    private final EpayHttpClient http;
    private final Sleeper sleeper;

    Transport(
            String apiKey,
            String baseUrl,
            Duration timeout,
            int maxRetries,
            Map<String, String> defaultHeaders,
            EpayHttpClient http,
            Sleeper sleeper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.http = http;
        this.sleeper = sleeper;
    }

    /**
     * Sends a request, retrying transient failures, and returns the parsed body.
     *
     * @param retryable null for the default, which retries GET only; a POST is
     *     retryable when every attempt carries the same idempotency key
     * @return the parsed JSON, the raw text when the body was not JSON, or null
     *     for an empty body
     */
    Object request(
            String method,
            String path,
            Map<String, String> query,
            Object body,
            Map<String, String> headers,
            Boolean retryable) {
        String label = method + " " + path;
        URI uri = buildUri(path, query);
        boolean isRetryable = retryable != null ? retryable : "GET".equals(method);
        int attempts = isRetryable ? maxRetries + 1 : 1;

        byte[] payload = body == null ? null : Json.write(body).getBytes(StandardCharsets.UTF_8);
        EpayHttpRequest request = new EpayHttpRequest(
                method, uri, buildHeaders(headers, payload != null), payload, timeout);

        for (int attempt = 0; ; attempt++) {
            boolean isLast = attempt == attempts - 1;

            EpayHttpResponse response;
            try {
                response = http.send(request);
            } catch (HttpTimeoutException | SocketTimeoutException e) {
                if (isLast) {
                    throw new EpayTimeoutException(String.format(Locale.ROOT,
                            "Request to %s timed out after %s", uri, timeout), e);
                }
                pause(attempt, null);
                continue;
            } catch (IOException e) {
                if (isLast) {
                    throw new EpayConnectionException(String.format(Locale.ROOT,
                            "Could not reach the ePay API at %s: %s", uri, e.getMessage()), e);
                }
                pause(attempt, null);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EpayConnectionException(label + " was interrupted", e);
            }

            int status = response.status();
            if (status >= 200 && status < 300) {
                return parseBody(response);
            }

            if (isLast || !shouldRetryStatus(status)) {
                throw EpayApiException.of(status, parseBody(response), flattenHeaders(response), label);
            }

            pause(attempt, response.header("Retry-After").orElse(null));
        }
    }

    /**
     * Expects the response to be a JSON object, as every modelled endpoint returns.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> requestObject(
            String method,
            String path,
            Map<String, String> query,
            Object body,
            Map<String, String> headers,
            Boolean retryable) {
        Object parsed = request(method, path, query, body, headers, retryable);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new EpayException("Unexpected response from " + method + " " + path
                + ": expected a JSON object, received " + describe(parsed));
    }

    private static String describe(Object value) {
        if (value == null) {
            return "an empty body";
        }
        if (value instanceof String) {
            return "non-JSON text";
        }
        return value instanceof List ? "a JSON array" : "a JSON " + value.getClass().getSimpleName();
    }

    private URI buildUri(String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append(path.startsWith("/") ? path : "/" + path);
        if (query != null) {
            char separator = '?';
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                url.append(separator)
                        .append(percentEncode(entry.getKey()))
                        .append('=')
                        .append(percentEncode(entry.getValue()));
                separator = '&';
            }
        }
        return URI.create(url.toString());
    }

    private Map<String, String> buildHeaders(Map<String, String> extra, boolean hasBody) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("Accept", "application/json");
        merged.put("Authorization", "Bearer " + apiKey);
        merged.put("User-Agent", "epay-java/" + Epay.VERSION + " java/" + Runtime.version().feature());
        merged.putAll(defaultHeaders);
        if (extra != null) {
            merged.putAll(extra);
        }
        if (hasBody) {
            merged.put("Content-Type", "application/json");
        }
        return merged;
    }

    private static boolean shouldRetryStatus(int status) {
        return status >= 500 || status == 408 || status == 409 || status == 429;
    }

    private void pause(int attempt, String retryAfter) {
        try {
            sleeper.sleep(backoffDelay(attempt, retryAfter));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EpayConnectionException("Interrupted while waiting to retry", e);
        }
    }

    /**
     * Exponential backoff with jitter, honouring {@code Retry-After} when present.
     * Jitter keeps concurrent clients from retrying in lockstep.
     */
    static Duration backoffDelay(int attempt, String retryAfter) {
        Duration max = Duration.ofMillis(BACKOFF_MAX_MS);
        if (retryAfter != null) {
            Duration requested = RetryAfter.parse(retryAfter).orElse(null);
            if (requested != null) {
                return requested.compareTo(max) > 0 ? max : requested;
            }
        }
        long ceiling = Math.min(BACKOFF_BASE_MS << Math.min(attempt, 20), BACKOFF_MAX_MS);
        long jittered = ceiling / 2 + ThreadLocalRandom.current().nextLong(ceiling / 2 + 1);
        return Duration.ofMillis(jittered);
    }

    private static Map<String, String> flattenHeaders(EpayHttpResponse response) {
        Map<String, String> flat = new LinkedHashMap<>();
        response.headers().forEach((name, values) -> flat.put(name, String.join(", ", values)));
        return flat;
    }

    /**
     * Parses a body as JSON, falling back to the raw text. {@code 204 No Content},
     * returned by cancel, and empty bodies become null.
     */
    private static Object parseBody(EpayHttpResponse response) {
        if (response.status() == 204) {
            return null;
        }
        String text = new String(response.body(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Json.parse(text);
        } catch (Json.JsonException e) {
            return text;
        }
    }

    /** Percent-encodes everything outside the RFC 3986 unreserved set. */
    static String percentEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }

    /** A per-call key, so an internal retry of initialize cannot create a second transaction. */
    static String newIdempotencyKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "epay_" + HexFormat.of().formatHex(bytes);
    }
}
