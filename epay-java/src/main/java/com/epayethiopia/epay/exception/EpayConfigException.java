package com.epayethiopia.epay.exception;

/** The client was constructed with unusable options. */
public class EpayConfigException extends EpayException {
    private static final long serialVersionUID = 1L;

    public EpayConfigException(String message) {
        super(message);
    }

    public EpayConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
