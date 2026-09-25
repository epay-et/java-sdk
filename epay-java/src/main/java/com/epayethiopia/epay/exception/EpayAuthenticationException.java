package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 401} — the API key is missing, invalid, or revoked. */
public class EpayAuthenticationException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayAuthenticationException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
