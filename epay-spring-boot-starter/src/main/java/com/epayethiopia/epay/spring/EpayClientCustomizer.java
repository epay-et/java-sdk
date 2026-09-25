package com.epayethiopia.epay.spring;

import com.epayethiopia.epay.Epay;

/**
 * Adjusts the auto-configured client's builder after properties are applied.
 *
 * <pre>{@code
 * @Bean
 * EpayClientCustomizer traceHeader() {
 *     return builder -> builder.header("X-Service", "checkout");
 * }
 * }</pre>
 *
 * <p>Every customizer bean is applied, in {@code @Order}. Define your own
 * {@link Epay} bean instead to replace the auto-configured one entirely.
 */
@FunctionalInterface
public interface EpayClientCustomizer {
    void customize(Epay.Builder builder);
}
