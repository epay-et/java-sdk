package com.epayethiopia.epay.exception;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Base class for any non-2xx HTTP response.
 *
 * <p>Callers branch on the subclass rather than inspecting status numbers. Use
 * {@link #of} to build the most specific subclass for a status.
 */
public class EpayApiException extends EpayException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final transient Object body;
    private final transient Map<String, String> headers;
    private final String request;

    /**
     * @param message human-readable description, already including the request
     * @param status HTTP status code of the response
     * @param body parsed JSON body, raw text when it was not JSON, or null
     * @param headers response headers; names are matched case-insensitively
     * @param request method and path of the originating request, such as {@code GET /transactions}
     */
    public EpayApiException(
            String message, int status, Object body, Map<String, String> headers, String request) {
        super(message);
        this.status = status;
        this.body = body;
        TreeMap<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            copy.putAll(headers);
        }
        this.headers = Collections.unmodifiableMap(copy);
        this.request = request == null ? "" : request;
    }

    /** The HTTP status code. */
    public int status() {
        return status;
    }

    /**
     * The parsed body: a {@code Map} or {@code List} for JSON, a {@code String}
     * when the body was not JSON, or null when it was empty.
     */
    public Object body() {
        return body;
    }

    /** Response headers, looked up case-insensitively. */
    public Map<String, String> headers() {
        return headers;
    }

    /** A single response header, looked up case-insensitively. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    /** Method and path of the request that failed, such as {@code GET /transactions}. */
    public String request() {
        return request;
    }

    /** Builds the most specific exception for a response. */
    public static EpayApiException of(
            int status, Object body, Map<String, String> headers, String request) {
        String message = extractMessage(body, status);
        String detail = request == null || request.isEmpty()
                ? message
                : String.format(Locale.ROOT, "%s failed with %d: %s", request, status, message);

        if (status >= 500) {
            return new EpayServerException(detail, status, body, headers, request);
        }
        return switch (status) {
            case 400 -> new EpayBadRequestException(detail, status, body, headers, request);
            case 401 -> new EpayAuthenticationException(detail, status, body, headers, request);
            case 403 -> new EpayPermissionDeniedException(detail, status, body, headers, request);
            case 404 -> new EpayNotFoundException(detail, status, body, headers, request);
            case 409 -> new EpayConflictException(detail, status, body, headers, request);
            case 422 -> new EpayUnprocessableEntityException(detail, status, body, headers, request);
            case 429 -> new EpayRateLimitException(detail, status, body, headers, request);
            default -> new EpayApiException(detail, status, body, headers, request);
        };
    }

    /**
     * Extracts a human-readable message from an error body.
     *
     * <p>The API uses two envelopes: {@code {"status":"error","data":{"message":…}}}
     * on the transaction endpoints and {@code {"statusCode","message","error"}} on
     * the provider endpoints, where {@code message} may itself be a list of
     * validation strings.
     */
    public static String extractMessage(Object body, int status) {
        String fallback = "HTTP " + status;

        if (body instanceof String text) {
            return text.isBlank() ? fallback : text.strip();
        }
        if (!(body instanceof Map<?, ?> map)) {
            return fallback;
        }

        if (map.get("data") instanceof Map<?, ?> data) {
            String nested = flatten(data.get("message"));
            if (nested != null) {
                return nested;
            }
        }

        String flattened = flatten(map.get("message"));
        if (flattened != null) {
            return flattened;
        }

        if (map.get("error") instanceof String error && !error.isBlank()) {
            return error.strip();
        }

        return fallback;
    }

    private static String flatten(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.strip();
        }
        if (value instanceof List<?> list) {
            List<String> parts = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(Objects::nonNull)
                    .toList();
            if (!parts.isEmpty()) {
                return String.join("; ", parts);
            }
        }
        return null;
    }
}
