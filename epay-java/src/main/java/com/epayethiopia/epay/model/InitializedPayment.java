package com.epayethiopia.epay.model;

import java.time.Instant;

/**
 * A new payment session, from {@code POST /v1/transactions/initialize}.
 *
 * @param reference the ePay reference; store it to reconcile the payment later
 * @param checkoutUrl where to redirect the customer to pay
 * @param status always {@code "success"} on a successful initialization
 * @param expiresAt when the checkout session expires
 */
public record InitializedPayment(
        String reference, String checkoutUrl, String status, Instant expiresAt) {}
