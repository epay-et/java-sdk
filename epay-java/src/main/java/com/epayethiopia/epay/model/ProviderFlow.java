package com.epayethiopia.epay.model;

/**
 * A payment flow a provider supports.
 *
 * @param flowCode identifies the flow, such as {@code USSD}, {@code SMS_OTP}, or
 *     {@code IN_APP}; pass it to {@code DirectPaymentParams.Builder#flowCode}
 * @param isDefault whether this is the provider's recommended flow
 * @param supportsDirectPayment whether a direct payment accepts this flow
 */
public record ProviderFlow(String flowCode, boolean isDefault, boolean supportsDirectPayment) {}
