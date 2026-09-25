package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 5xx} — the API failed; already retried before surfacing. */
public class EpayServerException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayServerException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
