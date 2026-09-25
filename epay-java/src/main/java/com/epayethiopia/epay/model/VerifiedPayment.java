package com.epayethiopia.epay.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The receipt for a completed transaction, from {@code GET /v1/transactions/:reference/verify}.
 *
 * @param reference the ePay reference
 * @param merchantReference your own reference, or null if none was given
 * @param status always {@link TransactionStatus#COMPLETED}; the API rejects anything else
 * @param amount the amount charged
 * @param serviceFee the fee, or null
 * @param currencyCode an ISO 4217 code, or null
 * @param paymentMethod the provider the customer paid with, or null
 * @param customer the customer's details
 * @param paidAt when the payment completed
 * @param createdAt when the transaction was created
 */
public record VerifiedPayment(
        String reference,
        String merchantReference,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal serviceFee,
        String currencyCode,
        String paymentMethod,
        Customer customer,
        Instant paidAt,
        Instant createdAt) {}
