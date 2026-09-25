package com.epayethiopia.epay.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.epayethiopia.epay.Epay;
import com.epayethiopia.epay.exception.EpayConfigException;
import com.epayethiopia.epay.http.EpayHttpClient;
import com.epayethiopia.epay.http.EpayHttpRequest;
import com.epayethiopia.epay.http.EpayHttpResponse;
import com.epayethiopia.epay.model.Mode;

class EpayAutoConfigurationTest {

    static final String KEY = "sk_test_abcdefghijklmnop";

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EpayAutoConfiguration.class));

    @Test
    void configuresTheClientFromProperties() {
        runner.withPropertyValues(
                        "epay.api-key=" + KEY,
                        "epay.base-url=https://sandbox.example/v1",
                        "epay.webhook-secret=whsec_x",
                        "epay.timeout=5s",
                        "epay.max-retries=0")
                .run(context -> {
                    Epay epay = context.getBean(Epay.class);
                    assertThat(epay.mode()).contains(Mode.SANDBOX);
                    assertThat(epay.baseUrl()).isEqualTo("https://sandbox.example/v1");
                    assertThat(epay.webhooks().isConfigured()).isTrue();

                    EpayProperties properties = context.getBean(EpayProperties.class);
                    assertThat(properties.getTimeout()).hasSeconds(5);
                    assertThat(properties.toString()).doesNotContain(KEY).doesNotContain("whsec_x");
                });
    }

    @Test
    void failsFastWithoutAKey() {
        assumeThat(System.getenv("EPAY_SECRET_KEY")).isNull();
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(EpayConfigException.class);
        });
    }

    @Test
    void explainsTheStartupFailure() {
        EpayConfigException cause = new EpayConfigException("Missing ePay API key.");
        FailureAnalysis analysis = new EpayConfigFailureAnalyzer().analyze(new RuntimeException(cause), cause);
        assertThat(analysis.getDescription()).contains("Missing ePay API key.");
        assertThat(analysis.getAction()).contains("epay.api-key").contains("epay.enabled=false");
    }

    @Test
    void canBeSwitchedOff() {
        runner.withPropertyValues("epay.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(Epay.class));
    }

    @Test
    void backsOffForAUserDefinedClient() {
        runner.withUserConfiguration(OwnClient.class)
                .run(context -> assertThat(context.getBean(Epay.class)).isSameAs(OwnClient.CLIENT));
    }

    @Test
    void appliesTheHttpClientBeanAndCustomizers() {
        runner.withPropertyValues("epay.api-key=" + KEY, "epay.headers.X-Team=payments")
                .withUserConfiguration(Recording.class)
                .run(context -> {
                    context.getBean(Epay.class).request("GET", "/ping", Map.of(), null);

                    List<EpayHttpRequest> sent = context.getBean(Recording.class).sent;
                    assertThat(sent).singleElement().satisfies(request -> assertThat(request.headers())
                            .containsEntry("X-Team", "payments")
                            .containsEntry("X-Customized", "yes"));
                });
    }

    @Test
    void registersWebhookSupportOnlyInServletApps() {
        runner.withPropertyValues("epay.api-key=" + KEY)
                .run(context -> assertThat(context).doesNotHaveBean(EpayWebhookArgumentResolver.class));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EpayAutoConfiguration.class))
                .withPropertyValues("epay.api-key=" + KEY)
                .run(context -> assertThat(context).hasSingleBean(EpayWebhookArgumentResolver.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClient {
        static final Epay CLIENT = Epay.builder().apiKey("sk_live_abcdefghijklmnop").build();

        @Bean
        Epay epay() {
            return CLIENT;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Recording {
        final List<EpayHttpRequest> sent = new ArrayList<>();

        @Bean
        EpayHttpClient recordingHttpClient() {
            return request -> {
                sent.add(request);
                return EpayHttpResponse.of(200, "{}");
            };
        }

        @Bean
        EpayClientCustomizer customizer() {
            return builder -> builder.header("X-Customized", "yes");
        }
    }
}
