package com.epayethiopia.epay;

import java.util.Map;

import com.epayethiopia.epay.model.Timeline;
import com.epayethiopia.epay.model.Transaction;

/** Read operations over transaction history. */
public final class Transactions {

    /** Fixed page size of {@code GET /v1/transactions}. */
    public static final int PAGE_SIZE = 10;

    private final Transport transport;

    Transactions(Transport transport) {
        this.transport = transport;
    }

    /** The most recent page of transactions, newest first. */
    public TransactionPage list() {
        return list(ListParams.none());
    }

    /**
     * A page of transactions, newest first, {@value #PAGE_SIZE} per page.
     *
     * <p>Iterate the returned page to walk every following page lazily, carrying
     * the filters along.
     */
    public TransactionPage list(ListParams params) {
        Map<String, Object> json = transport.requestObject("GET", "/transactions", params.toQuery(), null, null, null);
        return new TransactionPage(
                Decoder.transactions(json.get("data")),
                Decoder.string(json, "nextCursor"),
                Decoder.bool(json, "hasMore"),
                cursor -> list(params.withCursor(cursor)));
    }

    /**
     * The current state of a single transaction.
     *
     * @throws com.epayethiopia.epay.exception.EpayNotFoundException {@code 404} — no such transaction
     */
    public Transaction retrieve(String reference) {
        String path = "/transactions/" + Transport.percentEncode(Validation.requireReference(reference));
        return Decoder.transaction(transport.requestObject("GET", path, null, null, null, null));
    }

    /**
     * Every event recorded against a transaction, earliest first. Empty until the
     * first provider event arrives.
     *
     * @throws com.epayethiopia.epay.exception.EpayNotFoundException {@code 404} — no such transaction
     */
    public Timeline timeline(String reference) {
        String path = "/transactions/" + Transport.percentEncode(Validation.requireReference(reference)) + "/timeline";
        return Decoder.timeline(transport.requestObject("GET", path, null, null, null, null));
    }
}
