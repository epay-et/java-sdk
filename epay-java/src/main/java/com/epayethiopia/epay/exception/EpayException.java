package com.epayethiopia.epay.exception;

/**
 * Base class for every exception the SDK throws.
 *
 * <p>Unchecked, so callers catch only the failures they can act on.
 */
public class EpayException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EpayException(String message) {
        super(message);
    }

    public EpayException(String message, Throwable cause) {
        super(message, cause);
    }
}
