package com.epayethiopia.epay.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.epayethiopia.epay.Webhooks;
import com.epayethiopia.epay.model.EventType;
import com.epayethiopia.epay.model.WebhookEvent;

class EpayWebhookTest {

    static final String SECRET = "whsec_test_secret";
    // Deliberately unusual formatting: any re-serialization would change the digest.
    static final String BODY = "{ \"event\" : \"payment.success\",\n  \"reference\":\"P1\", \"amount\":\"250.00\" }";
    static final String SIGNATURE = "sha256=" + Webhooks.withSecret(SECRET).sign(BODY);

    @RestController
    static class WebhookController {
        @PostMapping("/webhooks/epay")
        String handle(@EpayWebhook WebhookEvent event) {
            return event.type() + " " + event.reference() + " " + event.amount();
        }
    }

    static MockMvc standalone(Filter... filters) {
        return MockMvcBuilders.standaloneSetup(new WebhookController())
                .setCustomArgumentResolvers(new EpayWebhookArgumentResolver(Webhooks.withSecret(SECRET)))
                .addFilters(filters)
                .build();
    }

    @Test
    void passesAVerifiedEventToTheHandler() throws Exception {
        standalone().perform(post("/webhooks/epay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Webhooks.SIGNATURE_HEADER, SIGNATURE)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().string("payment.success P1 250.00"));
    }

    @Test
    void answers401ForABadOrMissingSignature() throws Exception {
        MockMvc mvc = standalone();
        mvc.perform(post("/webhooks/epay").contentType(MediaType.APPLICATION_JSON)
                        .header(Webhooks.SIGNATURE_HEADER, "sha256=" + "0".repeat(64)).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/epay").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void answers400ForASignedBodyThatIsNotAnObject() throws Exception {
        String array = "[1]";
        standalone().perform(post("/webhooks/epay").contentType(MediaType.APPLICATION_JSON)
                        .header(Webhooks.SIGNATURE_HEADER, Webhooks.withSecret(SECRET).sign(array)).content(array))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readsTheCachedCopyWhenALoggingFilterDrainedTheBody() throws Exception {
        Filter drainingLogger = (request, response, chain) -> {
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper((HttpServletRequest) request, 1 << 16);
            logBody(wrapped);
            chain.doFilter(wrapped, response);
        };

        standalone(drainingLogger).perform(post("/webhooks/epay").contentType(MediaType.APPLICATION_JSON)
                        .header(Webhooks.SIGNATURE_HEADER, SIGNATURE).content(BODY))
                .andExpect(status().isOk());
    }

    private static void logBody(ContentCachingRequestWrapper request) throws IOException {
        byte[] drained = request.getInputStream().readAllBytes();
        assertThat(new String(drained, StandardCharsets.UTF_8)).isEqualTo(BODY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebApp {
        @org.springframework.context.annotation.Bean
        WebhookController webhookController() {
            return new WebhookController();
        }
    }

    @Test
    void theAutoConfigurationRegistersTheResolver() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EpayAutoConfiguration.class))
                .withUserConfiguration(WebApp.class)
                .withPropertyValues("epay.api-key=sk_test_abcdefghijklmnop", "epay.webhook-secret=" + SECRET)
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
                    mvc.perform(post("/webhooks/epay").contentType(MediaType.APPLICATION_JSON)
                                    .header(Webhooks.SIGNATURE_HEADER, SIGNATURE).content(BODY))
                            .andExpect(status().isOk())
                            .andExpect(content().string("payment.success P1 250.00"));
                    assertThat(EventType.fromValue("payment.success")).isEqualTo(EventType.PAYMENT_SUCCESS);
                });
    }
}
