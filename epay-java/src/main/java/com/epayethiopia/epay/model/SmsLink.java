package com.epayethiopia.epay.model;

import java.math.BigDecimal;

/**
 * A checkout link texted to a customer, from {@code POST /v1/transactions/sms-link}.
 *
 * @param message confirmation that the SMS was dispatched
 * @param checkoutUrl the signed link sent to the customer, valid for 1 hour
 * @param customerPhone the number the SMS was sent to
 * @param amount the pre-filled amount, or null when the customer chooses it
 * @param currencyCode an ISO 4217 code such as {@code ETB}
 */
public record SmsLink(
        String message, String checkoutUrl, String customerPhone, BigDecimal amount, String currencyCode) {}
