package com.epayethiopia.epay.model;

import java.util.List;
import java.util.Optional;

/**
 * A provider from {@code GET /v1/payment-providers}, with the flows it supports.
 *
 * @param providerCode identifies the provider, such as {@code TELEBIRR}; pass it to
 *     {@code DirectPaymentParams.Builder#pspCode}
 * @param providerName the display name, or null
 * @param providerCategory commonly {@code wallet} or {@code bank}, or null
 * @param flows the payment flows the provider supports
 */
public record Provider(
        String providerCode, String providerName, String providerCategory, List<ProviderFlow> flows) {

    public Provider {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }

    /** The provider's recommended flow, if it marks one. */
    public Optional<ProviderFlow> defaultFlow() {
        return flows.stream().filter(ProviderFlow::isDefault).findFirst();
    }

    /** Whether any of the provider's flows accepts a direct payment. */
    public boolean supportsDirectPayment() {
        return flows.stream().anyMatch(ProviderFlow::supportsDirectPayment);
    }
}
