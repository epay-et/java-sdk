package com.epayethiopia.epay;

import java.util.List;

import com.epayethiopia.epay.model.Provider;
import com.epayethiopia.epay.model.ProviderFlow;
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
     * Every provider available to the account, each with the payment flows it
     * supports. Requires the {@code get_platform_payment_provider} permission.
     *
     * @throws com.epayethiopia.epay.exception.EpayPermissionDeniedException {@code 403} — key lacks the permission
     */
    public List<Provider> getAll() {
        return Decoder.providers(transport.requestObject("GET", "/payment-providers", null, null, null, null));
    }

    /**
     * The providers that can take a direct payment, keeping only their
     * direct-payment flows. Providers with no such flow are left out.
     *
     * <p>Pass a provider's {@code providerCode} to
     * {@link DirectPaymentParams.Builder#pspCode}, and pre-select
     * {@link Provider#defaultFlow()}.
     */
    public List<Provider> listForDirectPayment() {
        return getAll().stream()
                .map(provider -> new Provider(
                        provider.providerCode(),
                        provider.providerName(),
                        provider.providerCategory(),
                        provider.flows().stream().filter(ProviderFlow::supportsDirectPayment).toList()))
                .filter(provider -> !provider.flows().isEmpty())
                .toList();
    }

    /**
     * Returns the same list as {@link #getAll()}.
     *
     * @deprecated The API no longer reports a per-provider enabled state: every
     *     provider it returns is available to the account. Use {@link #getAll()}
     *     or {@link #listForDirectPayment()}.
     */
    @Deprecated(since = "0.2.0")
    public List<Provider> listEnabled() {
        return getAll();
    }
}
