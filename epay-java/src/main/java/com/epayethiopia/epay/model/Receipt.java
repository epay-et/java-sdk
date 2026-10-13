package com.epayethiopia.epay.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The receipt for a transaction in a terminal state, from
 * {@code GET /v1/transactions/:reference/receipt}.
 *
 * @param reference the ePay reference
 * @param merchantReference your own reference, or null if none was given
 * @param status {@link TransactionStatus#COMPLETED}, {@link TransactionStatus#FAILED},
 *     or {@link TransactionStatus#CANCELLED}
 * @param amount the transaction amount
 * @param chargeAmount the total charged to the customer, fees included
 * @param feeAmount the fee, or null
 * @param currencyCode an ISO 4217 code, or null
 * @param paymentMethod the provider used, or null
 * @param customer the customer's details
 * @param paidAt when the payment completed; null for failed and cancelled transactions
 * @param createdAt when the transaction was created
 */
public record Receipt(
        String reference,
        String merchantReference,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal chargeAmount,
        BigDecimal feeAmount,
        String currencyCode,
        String paymentMethod,
        Customer customer,
        Instant paidAt,
        Instant createdAt) {}
