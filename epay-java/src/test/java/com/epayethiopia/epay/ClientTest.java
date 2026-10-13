package com.epayethiopia.epay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.epayethiopia.epay.exception.EpayApiException;
import com.epayethiopia.epay.exception.EpayAuthenticationException;
import com.epayethiopia.epay.exception.EpayBadRequestException;
import com.epayethiopia.epay.exception.EpayConfigException;
import com.epayethiopia.epay.exception.EpayConflictException;
import com.epayethiopia.epay.exception.EpayConnectionException;
import com.epayethiopia.epay.exception.EpayException;
import com.epayethiopia.epay.exception.EpayNotFoundException;
import com.epayethiopia.epay.exception.EpayPermissionDeniedException;
import com.epayethiopia.epay.exception.EpayRateLimitException;
import com.epayethiopia.epay.exception.EpayServerException;
import com.epayethiopia.epay.exception.EpayTimeoutException;
import com.epayethiopia.epay.exception.EpayUnprocessableEntityException;
import com.epayethiopia.epay.exception.EpayValidationException;
import com.epayethiopia.epay.http.EpayHttpRequest;
import com.epayethiopia.epay.model.DirectPayment;
import com.epayethiopia.epay.model.Mode;
import com.epayethiopia.epay.model.Provider;
import com.epayethiopia.epay.model.ProviderFlow;
import com.epayethiopia.epay.model.Receipt;
import com.epayethiopia.epay.model.SmsLink;
import com.epayethiopia.epay.model.Timeline;
import com.epayethiopia.epay.model.Transaction;
import com.epayethiopia.epay.model.TransactionStatus;
import com.epayethiopia.epay.model.VerifiedPayment;

class ClientTest {

    static final String KEY = "sk_test_abcdefghijklmnop";

    ScriptedHttpClient http;
    List<Duration> sleeps;

    @BeforeEach
    void setUp() {
        http = new ScriptedHttpClient();
        sleeps = new ArrayList<>();
    }

    Epay.Builder builder() {
        return Epay.builder()
                .apiKey(KEY)
                .httpClient(http)
                .environment(name -> null)
                .sleeper(sleeps::add);
    }

    Epay client() {
        return builder().build();
    }

    static InitializeParams params() {
        return InitializeParams.builder()
                .amount("250")
                .currencyCode("etb")
                .customerPhone("0911234567")
                .build();
    }

    static final String INITIALIZED = "{\"reference\":\"P1a2b3c4d20260813\","
            + "\"checkoutUrl\":\"https://checkout.example/P1a2b3c4d20260813\","
            + "\"status\":\"success\",\"expiresAt\":\"2026-08-13T12:30:00.000Z\"}";

    @Nested
    class Configuration {

        @Test
        void requiresAnApiKey() {
            assertThatThrownBy(() -> Epay.builder().environment(name -> null).build())
                    .isInstanceOf(EpayConfigException.class)
                    .hasMessageContaining("EPAY_SECRET_KEY");
        }

        @Test
        void rejectsPublishableOrMalformedKeys() {
            assertThatThrownBy(() -> Epay.builder().apiKey("pk_test_123").environment(name -> null).build())
                    .isInstanceOf(EpayConfigException.class)
                    .hasMessageContaining("sk_live_");
        }

        @Test
        void readsEveryOptionFromTheEnvironment() {
            Map<String, String> env = Map.of(
                    "EPAY_SECRET_KEY", " sk_live_abcdefghijklmnop ",
                    "EPAY_BASE_URL", "https://sandbox.example/v1/",
                    "EPAY_WEBHOOK_SECRET", "whsec_x");
            Epay epay = Epay.builder().environment(env::get).build();

            assertThat(epay.mode()).contains(Mode.LIVE);
            assertThat(epay.isSandbox()).isFalse();
            assertThat(epay.baseUrl()).isEqualTo("https://sandbox.example/v1");
            assertThat(epay.webhooks().isConfigured()).isTrue();
        }

        @Test
        void explicitOptionsWinOverTheEnvironment() {
            Epay epay = Epay.builder()
                    .apiKey(KEY)
                    .baseUrl("http://localhost:8080")
                    .environment(Map.of("EPAY_SECRET_KEY", "sk_live_zzzzzzzzzzzzzz", "EPAY_BASE_URL", "https://x.example")::get)
                    .build();
            assertThat(epay.mode()).contains(Mode.SANDBOX);
            assertThat(epay.baseUrl()).isEqualTo("http://localhost:8080");
        }

        @Test
        void defaultsToProduction() {
            assertThat(client().baseUrl()).isEqualTo("https://api.epayethiopia.com/v1");
        }

        @Test
        void toleratesAnUnrecognisedKeyEnvironment() {
            Epay epay = builder().apiKey("sk_future_abcdefghijklmnop").build();
            assertThat(epay.mode()).isEmpty();
            assertThat(epay.toString()).contains("mode=unknown");
        }

        @Test
        void neverPrintsTheKey() {
            assertThat(client().toString())
                    .isEqualTo("Epay(mode=sandbox, apiKey=sk_test_…mnop)")
                    .doesNotContain(KEY);
        }

        @Test
        void rejectsOutOfRangeOptions() {
            assertThatThrownBy(() -> builder().timeout(Duration.ZERO).build()).isInstanceOf(EpayConfigException.class);
            assertThatThrownBy(() -> builder().maxRetries(-1).build()).isInstanceOf(EpayConfigException.class);
            assertThatThrownBy(() -> builder().baseUrl("ftp://x.example").build()).isInstanceOf(EpayConfigException.class);
            assertThatThrownBy(() -> builder().baseUrl("not a url").build()).isInstanceOf(EpayConfigException.class);
            assertThatThrownBy(() -> builder().baseUrl("/v1").build()).isInstanceOf(EpayConfigException.class);
        }
    }

    @Nested
    class Initialize {

        @Test
        void sendsANormalizedBodyWithAuthentication() {
            http.reply(200, INITIALIZED);

            var session = client().payments().initialize(InitializeParams.builder()
                    .amount("250")
                    .currencyCode("etb")
                    .customerPhone("0911 234 567")
                    .merchantReference("order_123")
                    .returnUrl("https://shop.example/done")
                    .build());

            assertThat(session.reference()).isEqualTo("P1a2b3c4d20260813");
            assertThat(session.checkoutUrl()).isEqualTo("https://checkout.example/P1a2b3c4d20260813");
            assertThat(session.expiresAt()).isEqualTo(Instant.parse("2026-08-13T12:30:00Z"));

            EpayHttpRequest request = http.last();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri().toString()).isEqualTo("https://api.epayethiopia.com/v1/transactions/initialize");
            assertThat(request.headers())
                    .containsEntry("Authorization", "Bearer " + KEY)
                    .containsEntry("Content-Type", "application/json")
                    .containsEntry("Accept", "application/json")
                    .containsKey("x-idempotency-key");
            assertThat(request.headers().get("User-Agent")).startsWith("epay-java/" + Epay.VERSION);
            assertThat(ScriptedHttpClient.bodyOf(request)).isEqualTo("{\"amount\":\"250.00\",\"currencyCode\":\"ETB\","
                    + "\"customerPhone\":\"+251911234567\",\"merchantReference\":\"order_123\","
                    + "\"returnUrl\":\"https://shop.example/done\"}");
        }

        @Test
        void usesTheCallersIdempotencyKey() {
            http.reply(200, INITIALIZED);
            client().payments().initialize(InitializeParams.builder()
                    .amountMinorUnits(25_000).currencyCode("ETB").customerPhone("0911234567")
                    .idempotencyKey("order_123").build());
            assertThat(http.last().headers()).containsEntry("x-idempotency-key", "order_123");
        }

        @Test
        void retriesWithTheSameGeneratedKey() {
            http.reply(503, "{}").fail(new ConnectException("reset")).reply(200, INITIALIZED);

            client().payments().initialize(params());

            assertThat(http.requests).hasSize(3);
            List<String> keys = http.requests.stream().map(r -> r.headers().get("x-idempotency-key")).toList();
            assertThat(keys.get(0)).matches("epay_[0-9a-f]{32}");
            assertThat(keys).containsOnly(keys.get(0));
        }

        @Test
        void generatesAFreshKeyPerCall() {
            http.reply(200, INITIALIZED).reply(200, INITIALIZED);
            Epay epay = client();
            epay.payments().initialize(params());
            epay.payments().initialize(params());
            assertThat(http.requests.get(0).headers().get("x-idempotency-key"))
                    .isNotEqualTo(http.requests.get(1).headers().get("x-idempotency-key"));
        }

        @Test
        void validatesBeforeSending() {
            assertThatThrownBy(() -> InitializeParams.builder().currencyCode("ETB").customerPhone("0911234567").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("amount");
            assertThatThrownBy(() -> InitializeParams.builder().amount("1").customerPhone("0911234567").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("currencyCode");
            assertThatThrownBy(() -> InitializeParams.builder().amount("1").currencyCode("ETB").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("customerPhone");
            assertThatThrownBy(() -> InitializeParams.builder().amount("1").currencyCode("ETB")
                    .customerPhone("0911234567").idempotencyKey("k".repeat(256)).build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("255");
            assertThat(http.requests).isEmpty();
        }

        @Test
        void acceptsBigDecimalAmounts() {
            assertThat(InitializeParams.builder().amount(new BigDecimal("99.9")).currencyCode("ETB")
                    .customerPhone("0911234567").build().amount()).isEqualTo("99.90");
        }
    }

    @Nested
    class VerifyAndCancel {

        @Test
        void decodesTheReceipt() {
            http.reply(200, "{\"reference\":\"P1\",\"merchantReference\":\"order_123\",\"status\":\"completed\","
                    + "\"amount\":\"250.00\",\"serviceFee\":\"5.00\",\"currencyCode\":\"ETB\","
                    + "\"paymentMethod\":\"telebirr\",\"customer\":{\"name\":\"Abebe\",\"email\":null,"
                    + "\"phone\":\"+251911234567\"},\"paidAt\":\"2026-08-13T11:47:00.000Z\","
                    + "\"createdAt\":\"2026-08-13T11:30:00.000Z\",\"futureField\":1}");

            VerifiedPayment receipt = client().payments().verify(" P1 ");

            assertThat(http.last().uri().getPath()).isEqualTo("/v1/transactions/P1/verify");
            assertThat(receipt.status()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(receipt.amount()).isEqualByComparingTo("250.00");
            assertThat(receipt.amount().toPlainString()).isEqualTo("250.00");
            assertThat(receipt.serviceFee()).isEqualByComparingTo("5");
            assertThat(receipt.customer().name()).isEqualTo("Abebe");
            assertThat(receipt.customer().email()).isNull();
            assertThat(receipt.paidAt()).isEqualTo(Instant.parse("2026-08-13T11:47:00Z"));
        }

        @Test
        void encodesReferencesAsOnePathSegment() {
            http.reply(200, "{}");
            client().transactions().retrieve("a/b c?d");
            assertThat(http.last().uri().getRawPath()).isEqualTo("/v1/transactions/a%2Fb%20c%3Fd");
        }

        @Test
        void rejectsBlankReferencesWithoutARequest() {
            assertThatThrownBy(() -> client().payments().verify("  ")).isInstanceOf(EpayValidationException.class);
            assertThatThrownBy(() -> client().payments().cancel(null)).isInstanceOf(EpayValidationException.class);
            assertThat(http.requests).isEmpty();
        }

        @Test
        void cancelAcceptsNoContent() {
            http.reply(204, "");
            client().payments().cancel("P1");
            assertThat(http.last().method()).isEqualTo("POST");
            assertThat(http.last().body()).isNull();
            assertThat(http.last().headers()).doesNotContainKey("Content-Type");
        }

        @Test
        void cancelIsNeverRetried() {
            http.reply(503, "{}");
            assertThatThrownBy(() -> client().payments().cancel("P1")).isInstanceOf(EpayServerException.class);
            assertThat(http.requests).hasSize(1);
        }
    }

    @Nested
    class Retries {

        @Test
        void retriesServerErrorsThenSucceeds() {
            http.reply(500, "{}").reply(502, "{}").reply(200, "{\"reference\":\"P1\"}");
            assertThat(client().transactions().retrieve("P1").reference()).isEqualTo("P1");
            assertThat(http.requests).hasSize(3);
            assertThat(sleeps).hasSize(2);
        }

        @Test
        void surfacesTheLastErrorOnceTheBudgetIsSpent() {
            http.reply(500, "{}").reply(500, "{}").reply(500, "{\"message\":\"down\"}");
            assertThatThrownBy(() -> client().transactions().retrieve("P1"))
                    .isInstanceOf(EpayServerException.class)
                    .hasMessage("GET /transactions/P1 failed with 500: down");
            assertThat(http.requests).hasSize(3);
        }

        @Test
        void honoursMaxRetries() {
            http.reply(500, "{}");
            assertThatThrownBy(() -> builder().maxRetries(0).build().transactions().retrieve("P1"))
                    .isInstanceOf(EpayServerException.class);
            assertThat(http.requests).hasSize(1);
        }

        @Test
        void retriesConflictsAndRequestTimeouts() {
            http.reply(409, "{}").reply(408, "{}").reply(200, "{}");
            client().transactions().retrieve("P1");
            assertThat(http.requests).hasSize(3);
        }

        @Test
        void doesNotRetryClientErrors() {
            http.reply(400, "{}");
            assertThatThrownBy(() -> client().transactions().retrieve("P1")).isInstanceOf(EpayBadRequestException.class);
            assertThat(http.requests).hasSize(1);
        }

        @Test
        void honoursRetryAfterUpToTheCeiling() {
            http.reply(429, "{}", Map.of("retry-after", List.of("3")))
                    .reply(429, "{}", Map.of("Retry-After", List.of("120")))
                    .reply(200, "{}");
            client().transactions().retrieve("P1");
            assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(8));
        }

        @Test
        void backsOffExponentiallyWithJitter() {
            for (int i = 0; i < 200; i++) {
                assertThat(Transport.backoffDelay(0, null).toMillis()).isBetween(250L, 500L);
                assertThat(Transport.backoffDelay(2, null).toMillis()).isBetween(1000L, 2000L);
                assertThat(Transport.backoffDelay(30, null).toMillis()).isBetween(4000L, 8000L);
            }
        }

        @Test
        void mapsTimeoutsAndConnectionFailures() {
            http.fail(new HttpTimeoutException("slow")).fail(new HttpTimeoutException("slow"))
                    .fail(new HttpTimeoutException("slow"));
            assertThatThrownBy(() -> client().transactions().retrieve("P1"))
                    .isInstanceOf(EpayTimeoutException.class)
                    .isInstanceOf(EpayConnectionException.class)
                    .hasMessageContaining("timed out");

            http.fail(new ConnectException("refused")).fail(new ConnectException("refused"))
                    .fail(new ConnectException("refused"));
            assertThatThrownBy(() -> client().transactions().retrieve("P1"))
                    .isInstanceOf(EpayConnectionException.class)
                    .isNotInstanceOf(EpayTimeoutException.class)
                    .hasMessageContaining("refused")
                    .hasCauseInstanceOf(ConnectException.class);
        }

        @Test
        void stopsAndKeepsTheInterruptFlagWhenInterrupted() {
            http.then(request -> {
                throw new InterruptedException();
            });
            try {
                assertThatThrownBy(() -> client().transactions().retrieve("P1"))
                        .isInstanceOf(EpayConnectionException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(http.requests).hasSize(1);
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Nested
    class Errors {

        @ParameterizedTest
        @CsvSource({
            "400, com.epayethiopia.epay.exception.EpayBadRequestException",
            "401, com.epayethiopia.epay.exception.EpayAuthenticationException",
            "403, com.epayethiopia.epay.exception.EpayPermissionDeniedException",
            "404, com.epayethiopia.epay.exception.EpayNotFoundException",
            "409, com.epayethiopia.epay.exception.EpayConflictException",
            "422, com.epayethiopia.epay.exception.EpayUnprocessableEntityException",
            "429, com.epayethiopia.epay.exception.EpayRateLimitException",
            "500, com.epayethiopia.epay.exception.EpayServerException",
            "503, com.epayethiopia.epay.exception.EpayServerException",
            "418, com.epayethiopia.epay.exception.EpayApiException",
        })
        void mapsStatusesToExceptions(int status, String className) throws ClassNotFoundException {
            EpayApiException error = EpayApiException.of(status, null, Map.of(), "GET /x");
            assertThat(error.getClass()).isEqualTo(Class.forName(className));
            assertThat(error.status()).isEqualTo(status);
            assertThat(error).isInstanceOf(EpayException.class);
        }

        @Test
        void readsBothErrorEnvelopes() {
            http.reply(400, "{\"status\":\"error\",\"data\":{\"message\":\"Transaction is not completed\"}}");
            assertThatThrownBy(() -> client().payments().verify("P1"))
                    .isInstanceOf(EpayBadRequestException.class)
                    .hasMessage("GET /transactions/P1/verify failed with 400: Transaction is not completed");

            http.reply(403, "{\"statusCode\":403,\"message\":[\"missing permission\",\"ip not allowed\"],"
                    + "\"error\":\"Forbidden\"}");
            assertThatThrownBy(() -> client().paymentProviders().list())
                    .isInstanceOf(EpayPermissionDeniedException.class)
                    .hasMessageEndingWith("missing permission; ip not allowed");

            http.reply(401, "{\"statusCode\":401,\"error\":\"Unauthorized\"}");
            assertThatThrownBy(() -> client().paymentProviders().list())
                    .isInstanceOf(EpayAuthenticationException.class)
                    .hasMessageEndingWith(": Unauthorized");
        }

        @Test
        void keepsTheBodyAndHeaders() {
            http.reply(404, "<html>gone</html>", Map.of("X-Request-Id", List.of("req_1")));
            EpayNotFoundException error = catchApi(EpayNotFoundException.class, () -> client().transactions().retrieve("P1"));
            assertThat(error.body()).isEqualTo("<html>gone</html>");
            assertThat(error.header("x-request-id")).contains("req_1");
            assertThat(error.request()).isEqualTo("GET /transactions/P1");
            assertThat(error.getMessage()).endsWith(": <html>gone</html>");
        }

        @Test
        void exposesRetryAfterOnRateLimits() {
            http.reply(429, "{}", Map.of("Retry-After", List.of("7")))
                    .reply(429, "{}", Map.of("Retry-After", List.of("7")))
                    .reply(429, "{}", Map.of("Retry-After", List.of("7")));
            EpayRateLimitException error = catchApi(EpayRateLimitException.class, () -> client().transactions().retrieve("P1"));
            assertThat(error.retryAfter()).contains(Duration.ofSeconds(7));
        }

        @Test
        void rejectsANonObjectSuccessBody() {
            http.reply(200, "[1,2]");
            assertThatThrownBy(() -> client().transactions().retrieve("P1"))
                    .isExactlyInstanceOf(EpayException.class)
                    .hasMessageContaining("expected a JSON object, received a JSON array");
        }

        <T extends EpayApiException> T catchApi(Class<T> type, Runnable call) {
            try {
                call.run();
            } catch (EpayApiException e) {
                assertThat(e).isInstanceOf(type);
                return type.cast(e);
            }
            throw new AssertionError("expected " + type.getSimpleName());
        }
    }

    @Nested
    class Listing {

        static String page(String cursor, boolean hasMore, String... references) {
            String data = java.util.Arrays.stream(references)
                    .map(r -> "{\"reference\":\"" + r + "\",\"status\":\"completed\",\"amount\":\"1.00\","
                            + "\"currencyCode\":\"ETB\",\"createdAt\":\"2026-08-13T11:30:00.000Z\"}")
                    .collect(Collectors.joining(","));
            return "{\"data\":[" + data + "],\"nextCursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"")
                    + ",\"hasMore\":" + hasMore + "}";
        }

        @Test
        void sendsFiltersAsQueryParameters() {
            http.reply(200, page(null, false));
            client().transactions().list(ListParams.builder()
                    .from(java.time.LocalDate.of(2026, 8, 1))
                    .to("2026-08-31")
                    .currency("etb")
                    .status(TransactionStatus.COMPLETED)
                    .build());
            assertThat(http.last().uri().getRawQuery())
                    .isEqualTo("from=2026-08-01&to=2026-08-31&currency=ETB&status=completed");
        }

        @Test
        void walksEveryPageCarryingFilters() {
            http.reply(200, page("c1", true, "P1", "P2"))
                    .reply(200, page("c2", true, "P3"))
                    .reply(200, page(null, false, "P4"));

            List<String> references = new ArrayList<>();
            for (Transaction transaction : client().transactions().list(
                    ListParams.builder().status(TransactionStatus.COMPLETED).build())) {
                references.add(transaction.reference());
            }

            assertThat(references).containsExactly("P1", "P2", "P3", "P4");
            assertThat(http.requests).hasSize(3);
            assertThat(http.requests.get(1).uri().getRawQuery()).isEqualTo("status=completed&cursor=c1");
            assertThat(http.requests.get(2).uri().getRawQuery()).isEqualTo("status=completed&cursor=c2");
        }

        @Test
        void fetchesNoFurtherThanTheCallerReads() {
            http.reply(200, page("c1", true, "P1", "P2")).reply(200, page(null, false, "P3"));
            TransactionPage first = client().transactions().list();

            assertThat(first.stream().limit(2).map(Transaction::reference)).containsExactly("P1", "P2");
            assertThat(http.requests).hasSize(1);

            assertThat(first.data()).hasSize(2);
            assertThat(first.hasMore()).isTrue();
            assertThat(first.nextCursor()).contains("c1");
            assertThat(http.requests).hasSize(1);
        }

        @Test
        void collectsUpToALimit() {
            http.reply(200, page("c1", true, "P1", "P2")).reply(200, page("c2", true, "P3", "P4"));
            assertThat(client().transactions().list().toList(3)).extracting(Transaction::reference)
                    .containsExactly("P1", "P2", "P3");
            assertThat(http.requests).hasSize(2);
        }

        @Test
        void walksPageByPage() {
            http.reply(200, page("c1", true, "P1")).reply(200, page(null, false, "P2"));
            List<Integer> sizes = new ArrayList<>();
            for (TransactionPage page : client().transactions().list().pages()) {
                sizes.add(page.data().size());
            }
            assertThat(sizes).containsExactly(1, 1);
            assertThat(client().transactions()).isNotNull();
        }

        @Test
        void treatsAMissingCursorAsTheLastPage() {
            http.reply(200, page(null, true, "P1"));
            TransactionPage page = client().transactions().list();
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextPage()).isEmpty();
        }

        @Test
        void decodesUnknownStatusesWithoutFailing() {
            http.reply(200, "{\"data\":[{\"reference\":\"P1\",\"status\":\"disputed\",\"amount\":\"oops\","
                    + "\"createdAt\":\"not a date\"}],\"hasMore\":false}");
            Transaction transaction = client().transactions().list().data().get(0);
            assertThat(transaction.status()).isEqualTo(TransactionStatus.UNKNOWN);
            assertThat(transaction.amount()).isNull();
            assertThat(transaction.createdAt()).isNull();
        }

        @Test
        void rejectsInvertedRangesAndPlaceholderStatuses() {
            assertThatThrownBy(() -> ListParams.builder().from("2026-09-01").to("2026-08-01").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("must not be later");
            assertThatThrownBy(() -> ListParams.builder().status(TransactionStatus.UNKNOWN).build())
                    .isInstanceOf(EpayValidationException.class);
            assertThatThrownBy(() -> ListParams.builder().currency("dollars").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("currency");
        }

        @Test
        void decodesTimelines() {
            http.reply(200, "{\"reference\":\"P1\",\"events\":[{\"eventType\":\"session_created\","
                    + "\"occurredAt\":\"2026-08-13T11:30:00+03:00\"},{\"eventType\":\"payment_failed\","
                    + "\"occurredAt\":\"2026-08-13T11:31:00.000Z\",\"errorCode\":\"INVALID_OTP\","
                    + "\"errorMessage\":\"Wrong code\"}]}");
            Timeline timeline = client().transactions().timeline("P1");
            assertThat(timeline.events()).hasSize(2);
            assertThat(timeline.events().get(0).occurredAt()).isEqualTo(Instant.parse("2026-08-13T08:30:00Z"));
            assertThat(timeline.events().get(1).errorCode()).isEqualTo("INVALID_OTP");
        }
    }

    @Nested
    class Providers {

        static final String ALL = "{\"data\":[{\"providerCode\":\"TELEBIRR\",\"providerName\":\"TeleBirr\","
                + "\"providerCategory\":\"wallet\",\"flows\":["
                + "{\"flowCode\":\"USSD\",\"isDefault\":true,\"supportsDirectPayment\":true},"
                + "{\"flowCode\":\"SMS_OTP\",\"isDefault\":false,\"supportsDirectPayment\":false}]},"
                + "{\"providerCode\":\"CBE\",\"providerName\":\"CBE\",\"providerCategory\":\"bank\","
                + "\"flows\":[{\"flowCode\":\"QR\",\"isDefault\":true,\"supportsDirectPayment\":false}]}]}";

        @Test
        void listsCompactEntries() {
            http.reply(200, "{\"data\":[{\"providerCode\":\"telebirr\",\"providerName\":null,\"flowCode\":\"ussd\"}]}");
            var items = client().paymentProviders().list();
            assertThat(http.last().uri().getPath()).isEqualTo("/v1/payment-providers/list");
            assertThat(items).singleElement().satisfies(item -> {
                assertThat(item.providerCode()).isEqualTo("telebirr");
                assertThat(item.providerName()).isNull();
            });
        }

        @Test
        void decodesGroupedFlows() {
            http.reply(200, ALL);
            List<Provider> providers = client().paymentProviders().getAll();
            assertThat(http.last().uri().getPath()).isEqualTo("/v1/payment-providers");
            assertThat(providers).hasSize(2);
            assertThat(providers.get(0).flows()).extracting(ProviderFlow::flowCode).containsExactly("USSD", "SMS_OTP");
            assertThat(providers.get(0).defaultFlow()).map(ProviderFlow::flowCode).contains("USSD");
            assertThat(providers.get(1).supportsDirectPayment()).isFalse();
        }

        @Test
        void listsOnlyDirectPaymentFlows() {
            http.reply(200, ALL);
            List<Provider> providers = client().paymentProviders().listForDirectPayment();
            assertThat(providers).singleElement().satisfies(provider -> {
                assertThat(provider.providerCode()).isEqualTo("TELEBIRR");
                assertThat(provider.flows()).extracting(ProviderFlow::flowCode).containsExactly("USSD");
            });
        }

        @Test
        @SuppressWarnings("deprecation")
        void theDeprecatedListEnabledReturnsEveryProvider() {
            http.reply(200, ALL);
            assertThat(client().paymentProviders().listEnabled()).extracting(Provider::providerCode)
                    .containsExactly("TELEBIRR", "CBE");
        }
    }

    @Nested
    class DirectPaymentSmsLinkAndReceipt {

        static final String DIRECT = "{\"reference\":\"A3BX72KP\",\"status\":\"pending\","
                + "\"chargeAmount\":\"250.00\",\"feeAmount\":\"5.00\",\"attempt\":{\"session\":\"s1\"}}";

        static DirectPaymentParams.Builder charge() {
            return DirectPaymentParams.builder()
                    .amount("10").currencyCode("ETB").customerPhone("0911234567").pspCode("TELEBIRR");
        }

        @Test
        void directPaymentSendsANormalizedBody() {
            http.reply(200, DIRECT);

            DirectPayment charge = client().payments().directPayment(DirectPaymentParams.builder()
                    .amount(new BigDecimal("250"))
                    .currencyCode("etb")
                    .customerPhone("0911 234 567")
                    .pspCode(" TELEBIRR ")
                    .flowCode("USSD")
                    .merchantReference("order_456")
                    .idempotencyKey("order_456")
                    .build());

            assertThat(charge.reference()).isEqualTo("A3BX72KP");
            assertThat(charge.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(charge.feeAmount()).isEqualByComparingTo("5.00");
            assertThat(charge.attempt()).containsEntry("session", "s1");

            EpayHttpRequest request = http.last();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri().getPath()).isEqualTo("/v1/transactions/direct");
            assertThat(request.headers()).containsEntry("x-idempotency-key", "order_456");
            assertThat(ScriptedHttpClient.bodyOf(request)).isEqualTo("{\"amount\":\"250.00\",\"currencyCode\":\"ETB\","
                    + "\"customerPhone\":\"+251911234567\",\"pspCode\":\"TELEBIRR\",\"flowCode\":\"USSD\","
                    + "\"merchantReference\":\"order_456\"}");
        }

        @Test
        void directPaymentRetriesWithOneGeneratedKey() {
            http.reply(503, "{}").reply(200, DIRECT);

            client().payments().directPayment(charge().build());

            assertThat(http.requests).hasSize(2);
            String key = http.requests.get(0).headers().get("x-idempotency-key");
            assertThat(key).matches("epay_[0-9a-f]{32}");
            assertThat(http.requests.get(1).headers()).containsEntry("x-idempotency-key", key);
            assertThat(ScriptedHttpClient.bodyOf(http.requests.get(0))).doesNotContain("flowCode");
        }

        @Test
        void directPaymentValidatesBeforeSending() {
            assertThatThrownBy(() -> charge().pspCode("  ").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("pspCode");
            assertThatThrownBy(() -> charge().pspCode(null).build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("pspCode");
            assertThatThrownBy(() -> charge().flowCode(" ").build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("flowCode");
            assertThatThrownBy(() -> charge().idempotencyKey("k".repeat(256)).build())
                    .isInstanceOf(EpayValidationException.class).hasMessageContaining("255");
            assertThat(http.requests).isEmpty();
        }

        @Test
        void sendSmsLinkIsNormalizedAndNeverRetried() {
            http.reply(200, "{\"message\":\"Checkout link sent via SMS\",\"checkoutUrl\":\"https://x\","
                    + "\"customerPhone\":\"+251911234567\",\"amount\":\"500.00\",\"currencyCode\":\"ETB\"}");

            SmsLink link = client().payments().sendSmsLink(SmsLinkParams.builder()
                    .customerPhone("0911234567").currencyCode("etb").amount("500").build());

            assertThat(link.amount()).isEqualByComparingTo("500.00");
            assertThat(http.last().uri().getPath()).isEqualTo("/v1/transactions/sms-link");
            assertThat(ScriptedHttpClient.bodyOf(http.last()))
                    .isEqualTo("{\"customerPhone\":\"+251911234567\",\"currencyCode\":\"ETB\",\"amount\":\"500.00\"}");

            http.reply(503, "{}");
            assertThatThrownBy(() -> client().payments().sendSmsLink(SmsLinkParams.builder()
                            .customerPhone("0911234567").currencyCode("ETB").build()))
                    .isInstanceOf(EpayServerException.class);
            assertThat(http.requests).hasSize(2);
            assertThat(ScriptedHttpClient.bodyOf(http.last())).doesNotContain("amount");
        }

        @Test
        void receiptDecodesAnyTerminalStatus() {
            http.reply(200, "{\"reference\":\"A3BX72KP\",\"status\":\"failed\",\"amount\":\"250.00\","
                    + "\"chargeAmount\":\"250.00\",\"feeAmount\":null,\"customer\":{\"phone\":\"+251911234567\"},"
                    + "\"paidAt\":null,\"createdAt\":\"2026-10-12T09:10:00.000Z\"}");

            Receipt receipt = client().payments().receipt("A3BX72KP");

            assertThat(http.last().uri().getPath()).isEqualTo("/v1/transactions/A3BX72KP/receipt");
            assertThat(receipt.status()).isEqualTo(TransactionStatus.FAILED);
            assertThat(receipt.paidAt()).isNull();
            assertThat(receipt.feeAmount()).isNull();
            assertThat(receipt.createdAt()).isEqualTo(Instant.parse("2026-10-12T09:10:00Z"));
        }
    }

    @Test
    void reachesUnmodelledEndpoints() {
        http.reply(200, "{\"ok\":true}");
        Object result = client().request("get", "/some/new/endpoint", Map.of("limit", "10"), null);
        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(http.last().method()).isEqualTo("GET");
        assertThat(http.last().uri().toString()).isEqualTo("https://api.epayethiopia.com/v1/some/new/endpoint?limit=10");
    }

    @Test
    void addsDefaultHeaders() throws IOException {
        http.reply(200, "{}");
        builder().header("X-Trace", "abc").build().transactions().retrieve("P1");
        assertThat(http.last().headers()).containsEntry("X-Trace", "abc");
    }
}
