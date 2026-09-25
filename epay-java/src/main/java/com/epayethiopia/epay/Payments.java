package com.epayethiopia.epay;

import java.util.Map;

import com.epayethiopia.epay.model.InitializedPayment;
import com.epayethiopia.epay.model.VerifiedPayment;

/** Operations on the payment lifecycle. */
public final class Payments {

    private final Transport transport;

    Payments(Transport transport) {
        this.transport = transport;
    }

    /**
     * Creates a payment session and returns a hosted checkout URL.
     *
     * <p>Redirect the customer to {@link InitializedPayment#checkoutUrl()} and store
     * {@link InitializedPayment#reference()} to reconcile the payment later.
     *
     * <p>Safe to retry: every attempt carries the same idempotency key, either
     * yours or one generated for this call.
     */
    public InitializedPayment initialize(InitializeParams params) {
        String key = params.idempotencyKey().orElseGet(Transport::newIdempotencyKey);
        return Decoder.initializedPayment(transport.requestObject(
                "POST",
                "/transactions/initialize",
                null,
                params.toBody(),
                Map.of("x-idempotency-key", key),
                true));
    }

    /**
     * Returns the full receipt for a completed transaction. Call this before
     * fulfilling an order.
     *
     * <p>The API rejects any transaction that is not yet {@code completed}, so use
     * {@link Transactions#retrieve} first if you would rather branch on status
     * than catch an exception.
     *
     * @throws com.epayethiopia.epay.exception.EpayBadRequestException {@code 400} — not yet completed
     * @throws com.epayethiopia.epay.exception.EpayNotFoundException {@code 404} — no such transaction
     */
    public VerifiedPayment verify(String reference) {
        String path = "/transactions/" + Transport.percentEncode(Validation.requireReference(reference)) + "/verify";
        return Decoder.verifiedPayment(transport.requestObject("GET", path, null, null, null, null));
    }

    /**
     * Cancels a {@code pending} or {@code processing} transaction, firing a
     * {@code payment.cancelled} webhook. Cancellation is irreversible.
     *
     * <p>Never retried automatically: the endpoint takes no idempotency key, so a
     * retried attempt could act on a request the server already processed.
     *
     * @throws com.epayethiopia.epay.exception.EpayBadRequestException {@code 400} — not cancellable
     * @throws com.epayethiopia.epay.exception.EpayNotFoundException {@code 404} — no such transaction
     */
    public void cancel(String reference) {
        String path = "/transactions/" + Transport.percentEncode(Validation.requireReference(reference)) + "/cancel";
        transport.request("POST", path, null, null, null, false);
    }
}
