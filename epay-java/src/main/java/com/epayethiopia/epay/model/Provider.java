package com.epayethiopia.epay.model;

/**
 * A full provider entry from {@code GET /v1/payment-providers}.
 *
 * @param providerCode identifies the provider, such as {@code cbe_birr} or {@code telebirr}
 * @param providerName the display name, or null
 * @param providerCategory commonly {@code bank} or {@code mobile_money}, or null
 * @param flowCode the payment flow, such as {@code mobile_banking}, {@code ussd}, or {@code qr}
 * @param enabled whether your account has this provider enabled; disabled providers
 *     are still returned so your UI can show them as unavailable
 */
public record Provider(
        String providerCode,
        String providerName,
        String providerCategory,
        String flowCode,
        boolean enabled) {}
