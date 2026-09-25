package com.epayethiopia.epay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epayethiopia.epay.exception.EpayApiException;
import com.epayethiopia.epay.exception.EpayConnectionException;
import com.epayethiopia.epay.exception.EpayTimeoutException;
import com.sun.net.httpserver.HttpServer;

/** Exercises the default transport against a real local HTTP server. */
class JdkEpayHttpClientTest {

    HttpServer server;
    String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    Epay client(Duration timeout) {
        return Epay.builder()
                .apiKey("sk_test_abcdefghijklmnop")
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(0)
                .environment(name -> null)
                .build();
    }

    @Test
    void sendsARealRequest() {
        AtomicReference<String> seen = new AtomicReference<>();
        server.createContext("/v1/transactions/initialize", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.set(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Authorization")
                    + " " + exchange.getRequestHeaders().getFirst("x-idempotency-key")
                    + " " + exchange.getRequestHeaders().getFirst("Content-Type") + " " + body);
            byte[] reply = ClientTest.INITIALIZED.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });

        var session = client(Duration.ofSeconds(5)).payments().initialize(InitializeParams.builder()
                .amount("250").currencyCode("ETB").customerPhone("0911234567").idempotencyKey("order_1").build());

        assertThat(session.reference()).isEqualTo("P1a2b3c4d20260813");
        assertThat(seen.get()).isEqualTo("POST Bearer sk_test_abcdefghijklmnop order_1 application/json "
                + "{\"amount\":\"250.00\",\"currencyCode\":\"ETB\",\"customerPhone\":\"+251911234567\"}");
    }

    @Test
    void reportsASlowResponseAsATimeout() {
        server.createContext("/v1/transactions/P1", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).transactions().retrieve("P1"))
                .isInstanceOf(EpayTimeoutException.class);
    }

    @Test
    void neverFollowsRedirects() {
        server.createContext("/v1/transactions/P1", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://elsewhere.example/steal");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).transactions().retrieve("P1"))
                .isInstanceOfSatisfying(EpayApiException.class, e -> assertThat(e.status()).isEqualTo(302));
    }

    @Test
    void reportsARefusedConnection() {
        server.stop(0);
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).transactions().retrieve("P1"))
                .isInstanceOf(EpayConnectionException.class)
                .isNotInstanceOf(EpayTimeoutException.class);
    }
}
