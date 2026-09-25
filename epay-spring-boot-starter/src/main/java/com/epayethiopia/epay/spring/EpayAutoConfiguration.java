package com.epayethiopia.epay.spring;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.epayethiopia.epay.Epay;
import com.epayethiopia.epay.http.EpayHttpClient;

/**
 * Registers an {@link Epay} client configured from {@code epay.*} properties, and
 * in a servlet web application, support for {@link EpayWebhook @EpayWebhook}
 * parameters.
 *
 * <p>Define your own {@code Epay} bean to replace the client, or set
 * {@code epay.enabled=false} to turn the auto-configuration off.
 */
@AutoConfiguration
@ConditionalOnClass(Epay.class)
@ConditionalOnProperty(prefix = "epay", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EpayProperties.class)
public class EpayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Epay epay(
            EpayProperties properties,
            ObjectProvider<EpayHttpClient> httpClient,
            ObjectProvider<EpayClientCustomizer> customizers) {
        Epay.Builder builder = Epay.builder()
                .apiKey(properties.getApiKey())
                .webhookSecret(properties.getWebhookSecret())
                .baseUrl(properties.getBaseUrl())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries());
        properties.getHeaders().forEach(builder::header);
        httpClient.ifAvailable(builder::httpClient);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /** Webhook support for Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class WebhookConfiguration {

        @Bean
        @ConditionalOnMissingBean
        EpayWebhookArgumentResolver epayWebhookArgumentResolver(Epay epay) {
            return new EpayWebhookArgumentResolver(epay.webhooks());
        }

        @Bean
        WebMvcConfigurer epayWebMvcConfigurer(EpayWebhookArgumentResolver resolver) {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(resolver);
                }
            };
        }
    }
}
