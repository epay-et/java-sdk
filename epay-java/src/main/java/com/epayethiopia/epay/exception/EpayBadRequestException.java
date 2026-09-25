package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 400} — the request was rejected, for example verifying a transaction that is not yet completed. */
public class EpayBadRequestException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayBadRequestException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
