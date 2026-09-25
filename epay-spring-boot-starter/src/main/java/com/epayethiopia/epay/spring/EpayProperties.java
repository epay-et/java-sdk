package com.epayethiopia.epay.spring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the auto-configured {@link com.epayethiopia.epay.Epay} client.
 *
 * <p>Anything left unset falls back to the same environment variables the client
 * reads on its own: {@code EPAY_SECRET_KEY}, {@code EPAY_WEBHOOK_SECRET}, and
 * {@code EPAY_BASE_URL}.
 */
@ConfigurationProperties(prefix = "epay")
public class EpayProperties {

    /** Whether to auto-configure the client. */
    private boolean enabled = true;

    /**
     * Secret API key, sk_test_… or sk_live_…. Falls back to the EPAY_SECRET_KEY
     * environment variable.
     */
    private String apiKey;

    /** Webhook signing secret. Falls back to the EPAY_WEBHOOK_SECRET environment variable. */
    private String webhookSecret;

    /** API root. Falls back to the EPAY_BASE_URL environment variable, then production. */
    private String baseUrl;

    /** Per-attempt timeout. */
    private Duration timeout = Duration.ofSeconds(30);

    /** Retries after the first attempt, for 429, 5xx, and network failures. */
    private int maxRetries = 2;

    /** Headers added to every request. */
    private Map<String, String> headers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /** Keeps the secrets out of logs and actuator output. */
    @Override
    public String toString() {
        return "EpayProperties{enabled=" + enabled + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries + ", apiKey=" + (apiKey == null ? "unset" : "***")
                + ", webhookSecret=" + (webhookSecret == null ? "unset" : "***") + "}";
    }
}
