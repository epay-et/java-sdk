package com.epayethiopia.epay.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a verified webhook delivery to a controller parameter.
 *
 * <pre>{@code
 * @PostMapping("/webhooks/epay")
 * ResponseEntity<Void> handle(@EpayWebhook WebhookEvent event) {
 *     queue.publish(event);
 *     return ResponseEntity.ok().build();
 * }
 * }</pre>
 *
 * <p>The signature is checked against the raw request bytes before your method
 * runs. A missing or wrong signature answers {@code 401}, and a signed body that
 * is not a JSON object answers {@code 400}, so any event your method receives is
 * genuine.
 *
 * <p>Do not also declare an {@code @RequestBody} on the same method: that
 * consumes the body before it can be verified.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EpayWebhook {}
