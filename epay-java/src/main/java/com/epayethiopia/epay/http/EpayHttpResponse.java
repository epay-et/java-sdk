package com.epayethiopia.epay.http;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One received response, whatever its status.
 *
 * @param status the HTTP status code
 * @param headers response headers; names are matched case-insensitively
 * @param body the raw body, empty when there was none
 */
public record EpayHttpResponse(int status, Map<String, List<String>> headers, byte[] body) {

    public EpayHttpResponse {
        TreeMap<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, values) -> {
                // HTTP/2 pseudo-headers such as ":status" are not headers.
                if (name != null && !name.startsWith(":")) {
                    copy.put(name, List.copyOf(values));
                }
            });
        }
        headers = Collections.unmodifiableMap(copy);
        body = body == null ? new byte[0] : body.clone();
    }

    /** A copy of the body. */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /** The first value of a header, looked up case-insensitively. */
    public Optional<String> header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /** A response with a UTF-8 body and no headers, convenient in tests. */
    public static EpayHttpResponse of(int status, String body) {
        return new EpayHttpResponse(
                status, Map.of(), body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }
}
