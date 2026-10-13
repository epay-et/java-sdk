package com.epayethiopia.epay;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.epayethiopia.epay.exception.EpayConfigException;
import com.epayethiopia.epay.http.EpayHttpClient;
import com.epayethiopia.epay.http.JdkEpayHttpClient;
import com.epayethiopia.epay.model.Mode;

/**
 * Client for the ePay Business API. Thread-safe: build one and share it.
 *
 * <p>The key prefix selects the environment: {@code sk_test_…} hits the sandbox
 * and {@code sk_live_…} moves real money, so there is no separate mode to configure.
 *
 * <pre>{@code
 * Epay epay = Epay.builder()
 *         .apiKey(System.getenv("EPAY_SECRET_KEY"))
 *         .build();
 *
 * InitializedPayment session = epay.payments().initialize(InitializeParams.builder()
 *         .amount("250.00")
 *         .currencyCode("ETB")
 *         .customerPhone("+251911234567")
 *         .merchantReference("order_123")
 *         .idempotencyKey("order_123")
 *         .build());
 *
 * response.sendRedirect(session.checkoutUrl());
 * }</pre>
 *
 * <p>Failed requests throw an {@link com.epayethiopia.epay.exception.EpayApiException}
 * subclass. Transient failures — {@code 429}, {@code 5xx}, and network errors —
 * are retried with exponential backoff and jitter before surfacing.
 */
public final class Epay {

    /** The SDK version, sent in the User-Agent. */
    public static final String VERSION = "0.2.0";

    /** The production API root. */
    public static final String DEFAULT_BASE_URL = "https://api.epayethiopia.com/v1";

    /** The default per-attempt timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** The default number of retries after the first attempt. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    private final String apiKey;
    private final String baseUrl;
    private final Mode mode;
    private final Transport transport;
    private final Payments payments;
    private final Transactions transactions;
    private final PaymentProviders paymentProviders;
    private final Webhooks webhooks;

    private Epay(Builder builder) {
        UnaryOperator<String> env = builder.environment;

        String key = firstNonBlank(builder.apiKey, env.apply("EPAY_SECRET_KEY"));
        if (key == null) {
            throw new EpayConfigException(
                    "Missing ePay API key. Call apiKey(…) on the builder or set the EPAY_SECRET_KEY "
                            + "environment variable.");
        }
        key = key.strip();
        if (!key.startsWith("sk_")) {
            throw new EpayConfigException(
                    "Invalid ePay API key: expected a secret key beginning with \"sk_live_\" or "
                            + "\"sk_test_\". Secret keys are found in the dashboard under "
                            + "Developers -> API Keys.");
        }
        if (builder.timeout.isNegative() || builder.timeout.isZero()) {
            throw new EpayConfigException("timeout must be positive, received " + builder.timeout);
        }
        if (builder.maxRetries < 0) {
            throw new EpayConfigException("maxRetries must not be negative, received " + builder.maxRetries);
        }

        this.apiKey = key;
        this.baseUrl = validateBaseUrl(
                firstNonBlank(builder.baseUrl, env.apply("EPAY_BASE_URL"), DEFAULT_BASE_URL));
        this.mode = modeFromApiKey(key).orElse(null);

        EpayHttpClient http = builder.httpClient != null
                ? builder.httpClient
                : JdkEpayHttpClient.create(builder.timeout);
        this.transport = new Transport(
                key, baseUrl, builder.timeout, builder.maxRetries, builder.headers, http, builder.sleeper);

        this.payments = new Payments(transport);
        this.transactions = new Transactions(transport);
        this.paymentProviders = new PaymentProviders(transport);
        this.webhooks = new Webhooks(firstNonBlank(builder.webhookSecret, env.apply("EPAY_WEBHOOK_SECRET")));
    }

    /** A builder whose unset options fall back to environment variables. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A client configured entirely from {@code EPAY_SECRET_KEY},
     * {@code EPAY_WEBHOOK_SECRET}, and {@code EPAY_BASE_URL}.
     *
     * @throws EpayConfigException if {@code EPAY_SECRET_KEY} is unset or not a secret key
     */
    public static Epay fromEnvironment() {
        return builder().build();
    }

    /** Initialize, verify, and cancel payment sessions. */
    public Payments payments() {
        return payments;
    }

    /** List, retrieve, and inspect the timeline of transactions. */
    public Transactions transactions() {
        return transactions;
    }

    /** Discover which providers and flows the account can use. */
    public PaymentProviders paymentProviders() {
        return paymentProviders;
    }

    /** Verify webhook signatures and parse events. */
    public Webhooks webhooks() {
        return webhooks;
    }

    /** Which environment the key targets, or empty for a prefix this SDK version does not recognise. */
    public Optional<Mode> mode() {
        return Optional.ofNullable(mode);
    }

    /** Whether the configured key targets the sandbox. */
    public boolean isSandbox() {
        return mode == Mode.SANDBOX;
    }

    /** The API root every request is sent to. */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Escape hatch for endpoints this SDK version does not model yet, with the
     * same authentication, timeout, retry, and error handling as the typed methods.
     *
     * @param query query parameters; null values are dropped
     * @param body a JSON body built from maps, lists, strings, numbers, and booleans, or null
     * @return the parsed JSON — {@code Map}, {@code List}, {@code String},
     *     {@code BigDecimal}, {@code Boolean} — or null for an empty body
     */
    public Object request(String method, String path, Map<String, String> query, Object body) {
        return transport.request(method.toUpperCase(Locale.ROOT), path, query, body, null, null);
    }

    /** Renders the client without leaking the API key. */
    @Override
    public String toString() {
        return "Epay(mode=" + (mode == null ? "unknown" : mode.value())
                + ", apiKey=" + Validation.maskSecret(apiKey) + ")";
    }

    static Optional<Mode> modeFromApiKey(String apiKey) {
        if (apiKey.startsWith("sk_live_")) {
            return Optional.of(Mode.LIVE);
        }
        if (apiKey.startsWith("sk_test_")) {
            return Optional.of(Mode.SANDBOX);
        }
        return Optional.empty();
    }

    private static String validateBaseUrl(String value) {
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new EpayConfigException("baseUrl is not a valid URL: " + value);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null) {
            throw new EpayConfigException("baseUrl must be an absolute http(s) URL, received " + value);
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Configures an {@link Epay} client. */
    public static final class Builder {
        private String apiKey;
        private String webhookSecret;
        private String baseUrl;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private EpayHttpClient httpClient;
        private UnaryOperator<String> environment = System::getenv;
        private Transport.Sleeper sleeper = duration -> Thread.sleep(duration.toMillis());

        private Builder() {}

        /** The secret key. Defaults to {@code EPAY_SECRET_KEY}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** The webhook signing secret. Defaults to {@code EPAY_WEBHOOK_SECRET}. */
        public Builder webhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
            return this;
        }

        /** The API root. Defaults to {@code EPAY_BASE_URL}, then {@link #DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** The per-attempt timeout. Defaults to {@link #DEFAULT_TIMEOUT}. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * How many retries follow the first attempt, for {@code 429}, {@code 5xx},
         * and network failures. Defaults to {@link #DEFAULT_MAX_RETRIES}.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** A header added to every request. */
        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** The transport to send requests through, such as a scripted one in tests. */
        public Builder httpClient(EpayHttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /**
         * A {@code java.net.http} client to send requests through, for a proxy,
         * mTLS, or an executor. Leave redirects at {@link HttpClient.Redirect#NEVER}.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = new JdkEpayHttpClient(httpClient);
            return this;
        }

        /** Replaces environment lookups. Package-private: for tests. */
        Builder environment(UnaryOperator<String> environment) {
            this.environment = environment;
            return this;
        }

        /** Replaces the pause between retries. Package-private: for tests. */
        Builder sleeper(Transport.Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        /** @throws EpayConfigException if no API key is available, or an option is out of range */
        public Epay build() {
            return new Epay(this);
        }
    }
}
