package com.epayethiopia.epay.http;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One outgoing attempt.
 *
 * @param method the HTTP method, uppercase
 * @param uri the absolute request URI, query string included
 * @param headers request headers, authorization included
 * @param body the JSON body as UTF-8, or null for none
 * @param timeout how long this attempt may take
 */
public record EpayHttpRequest(
        String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {

    public EpayHttpRequest {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        body = body == null ? null : body.clone();
    }

    /** A copy of the body, or null. */
    @Override
    public byte[] body() {
        return body == null ? null : body.clone();
    }

    /** Excludes the headers, which carry the API key. */
    @Override
    public String toString() {
        return "EpayHttpRequest[" + method + " " + uri + "]";
    }
}
