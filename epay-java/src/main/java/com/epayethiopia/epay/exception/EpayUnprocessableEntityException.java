package com.epayethiopia.epay.exception;

import java.util.Map;

/** {@code 422} — the request was well-formed but semantically invalid. */
public class EpayUnprocessableEntityException extends EpayApiException {
    private static final long serialVersionUID = 1L;

    public EpayUnprocessableEntityException(String message, int status, Object body, Map<String, String> headers, String request) {
        super(message, status, body, headers, request);
    }
}
