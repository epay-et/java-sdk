package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 404} — no such resource. */
public class EpayNotFoundException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayNotFoundException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
