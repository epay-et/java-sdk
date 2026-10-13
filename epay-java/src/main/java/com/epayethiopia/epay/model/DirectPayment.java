package com.epayethiopia.epay.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A direct payment that has been sent to the customer's phone, from
 * {@code POST /v1/transactions/direct}.
 *
 * @param reference the ePay reference; store it to reconcile the payment
 * @param status always {@link TransactionStatus#PENDING}: the outcome arrives by
 *     webhook once the customer authorizes on their phone
 * @param chargeAmount the total charged to the customer, fees included
 * @param feeAmount the fee deducted from the transaction
 * @param attempt provider-specific session data whose shape varies by provider;
 *     inspect it only when building a custom authorization UI
 */
public record DirectPayment(
        String reference,
        TransactionStatus status,
        BigDecimal chargeAmount,
        BigDecimal feeAmount,
        Map<String, Object> attempt) {

    public DirectPayment {
        attempt = attempt == null ? Map.of() : attempt;
    }
}
