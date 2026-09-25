package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 403} — the IP is not whitelisted, or the key lacks a permission. */
public class EpayPermissionDeniedException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayPermissionDeniedException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
