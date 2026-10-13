package com.epayethiopia.epay;

import java.util.Map;

import com.epayethiopia.epay.model.DirectPayment;
import com.epayethiopia.epay.model.InitializedPayment;
import com.epayethiopia.epay.model.Receipt;
import com.epayethiopia.epay.model.SmsLink;
import com.epayethiopia.epay.model.VerifiedPayment;

/** Payment lifecycle: hosted checkout, direct payments, SMS links, receipts, and cancellation. */
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
     * Charges a customer's wallet or bank account directly, with no checkout page.
     *
     * <p>ePay pushes an authorization prompt (USSD menu, SMS OTP, or in-app
     * notification) to the customer's phone. This returns as soon as the prompt is
     * sent, with status {@code PENDING}; the outcome arrives as a
     * {@code payment.success} or {@code payment.failed} webhook once the customer acts.
     *
     * <p>Choose the provider and flow from {@link PaymentProviders#listForDirectPayment()}.
     *
     * <p>Safe to retry: every attempt carries the same idempotency key, either
     * yours or one generated for this call.
     *
     * @throws com.epayethiopia.epay.exception.EpayBadRequestException {@code 400} — amount, currency, or provider rejected
     * @throws com.epayethiopia.epay.exception.EpayPermissionDeniedException {@code 403} — direct payments are not enabled
     */
    public DirectPayment directPayment(DirectPaymentParams params) {
        String key = params.idempotencyKey().orElseGet(Transport::newIdempotencyKey);
        return Decoder.directPayment(transport.requestObject(
                "POST",
                "/transactions/direct",
                null,
                params.toBody(),
                Map.of("x-idempotency-key", key),
                true));
    }

    /**
     * Texts the customer a signed link to the hosted checkout page.
     *
     * <p>With an amount, the amount is signed into the link and cannot be changed;
     * without one, the customer enters it. The link is valid for 1 hour.
     *
     * <p>Never retried automatically: the endpoint takes no idempotency key, so a
     * retry after a lost response would text the customer a second time.
     *
     * @throws com.epayethiopia.epay.exception.EpayBadRequestException {@code 400} — phone number or currency rejected
     */
    public SmsLink sendSmsLink(SmsLinkParams params) {
        return Decoder.smsLink(transport.requestObject(
                "POST", "/transactions/sms-link", null, params.toBody(), null, false));
    }

    /**
     * Returns the receipt for a transaction in any terminal state: completed,
     * failed, or cancelled.
     *
     * <p>Use it for records, audits, and customer-facing receipts. To confirm a
     * payment before fulfilling an order, use {@link #verify}, which accepts only
     * completed transactions.
     *
     * @throws com.epayethiopia.epay.exception.EpayBadRequestException {@code 400} — still pending or processing
     * @throws com.epayethiopia.epay.exception.EpayNotFoundException {@code 404} — no such transaction
     */
    public Receipt receipt(String reference) {
        String path = "/transactions/" + Transport.percentEncode(Validation.requireReference(reference)) + "/receipt";
        return Decoder.receipt(transport.requestObject("GET", path, null, null, null, null));
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
