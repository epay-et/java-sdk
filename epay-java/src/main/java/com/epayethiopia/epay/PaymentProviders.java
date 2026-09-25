package com.epayethiopia.epay;

import java.util.List;

import com.epayethiopia.epay.model.Provider;
import com.epayethiopia.epay.model.ProviderItem;

/**
 * Read operations over the account's payment providers.
 *
 * <p>Both endpoints are mode-aware: a {@code sk_test_…} key returns sandbox
 * providers and a {@code sk_live_…} key returns live ones.
 */
public final class PaymentProviders {

    private final Transport transport;

    PaymentProviders(Transport transport) {
        this.transport = transport;
    }

    /**
     * A compact provider list suited to dropdowns. Requires the
     * {@code list_platform_payment_provider} permission.
     *
     * @throws com.epayethiopia.epay.exception.EpayPermissionDeniedException {@code 403} — key lacks the permission
     */
    public List<ProviderItem> list() {
        return Decoder.providerItems(transport.requestObject("GET", "/payment-providers/list", null, null, null, null));
    }

    /**
     * Every provider with its category and enabled state. Disabled providers are
     * included so your UI can show them as unavailable. Requires the
     * {@code get_platform_payment_provider} permission.
     *
     * @throws com.epayethiopia.epay.exception.EpayPermissionDeniedException {@code 403} — key lacks the permission
     */
    public List<Provider> getAll() {
        return Decoder.providers(transport.requestObject("GET", "/payment-providers", null, null, null, null));
    }

    /** Only the providers the account can currently route through. */
    public List<Provider> listEnabled() {
        return getAll().stream().filter(Provider::enabled).toList();
    }
}
