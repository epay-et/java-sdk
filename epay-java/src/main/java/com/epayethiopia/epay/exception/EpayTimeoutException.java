package com.epayethiopia.epay.exception;

/** An attempt exceeded the configured timeout. */
public class EpayTimeoutException extends EpayConnectionException {
    private static final long serialVersionUID = 1L;

    public EpayTimeoutException(String message) {
        super(message);
    }

    public EpayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
