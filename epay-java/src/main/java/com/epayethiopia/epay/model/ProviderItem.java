package com.epayethiopia.epay.model;

/**
 * A compact provider entry from {@code GET /v1/payment-providers/list}, suited to dropdowns.
 *
 * @param providerCode identifies the provider, such as {@code cbe_birr} or {@code telebirr}
 * @param providerName the display name, or null
 * @param flowCode the payment flow, such as {@code mobile_banking}, {@code ussd}, or {@code qr}
 */
public record ProviderItem(String providerCode, String providerName, String flowCode) {}
