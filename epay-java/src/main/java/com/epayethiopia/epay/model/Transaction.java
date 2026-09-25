package com.epayethiopia.epay.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction, as returned by retrieve and list.
 *
 * @param reference the ePay reference
 * @param merchantReference your own reference, or null if none was given
 * @param status the current lifecycle state
 * @param amount the amount, exact to two decimal places
 * @param currencyCode an ISO 4217 code such as {@code ETB}
 * @param paidAt when the payment completed, or null if it has not
 * @param createdAt when the transaction was created
 */
public record Transaction(
        String reference,
        String merchantReference,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        Instant paidAt,
        Instant createdAt) {}
