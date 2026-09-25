package com.epayethiopia.epay.exception;

/** A value failed local validation, so no request was sent. */
public class EpayValidationException extends EpayException {
    private static final long serialVersionUID = 1L;

    public EpayValidationException(String message) {
        super(message);
    }

    public EpayValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
