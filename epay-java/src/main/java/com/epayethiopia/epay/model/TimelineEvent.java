package com.epayethiopia.epay.model;

import java.time.Instant;

/**
 * A single entry in a transaction timeline.
 *
 * @param eventType what happened, such as {@code session_created} or {@code payment_failed}
 * @param occurredAt when it happened provider-side
 * @param errorCode the provider error code, set only on failure events
 * @param errorMessage a description of the error, set only on failure events
 */
public record TimelineEvent(
        String eventType, Instant occurredAt, String errorCode, String errorMessage) {}
