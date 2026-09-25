package com.epayethiopia.epay.exception;

/** No response was received from the API at all. */
public class EpayConnectionException extends EpayException {
    private static final long serialVersionUID = 1L;

    public EpayConnectionException(String message) {
        super(message);
    }

    public EpayConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
