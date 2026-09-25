package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 409} — the request conflicts with the current state. */
public class EpayConflictException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayConflictException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
