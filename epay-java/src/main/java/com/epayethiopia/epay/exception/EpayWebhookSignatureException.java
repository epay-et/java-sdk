package com.epayethiopia.epay.exception;

/** A webhook signature was missing, malformed, or did not match the body. */
public class EpayWebhookSignatureException extends EpayException {
    private static final long serialVersionUID = 1L;

    public EpayWebhookSignatureException(String message) {
        super(message);
    }

    public EpayWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
