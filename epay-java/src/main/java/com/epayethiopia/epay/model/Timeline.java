package com.epayethiopia.epay.model;

import java.util.List;

/**
 * Every event recorded against a transaction, from {@code GET /v1/transactions/:reference/timeline}.
 *
 * @param reference the ePay reference
 * @param events chronological, earliest first, and empty until the first provider event arrives
 */
public record Timeline(String reference, List<TimelineEvent> events) {
    public Timeline {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
