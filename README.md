# epay-et/java-sdk

Official ePay Business API client for Java, with a Spring Boot starter.

Accept payments from every major Ethiopian mobile wallet and bank with one integration.

Full API documentation: <https://docs.epayethiopia.com/>

- **Java 17+**, with immutable records for every response
- **No runtime dependencies**: HTTP is `java.net.http`, and JSON is handled internally, so the jar
  cannot conflict with your Jackson or HTTP client version
- **Automatic retries** with exponential backoff and jitter on `429`/`5xx`/network errors
- **Safe retries on initialize**: every attempt reuses one idempotency key
- **Exact money**: amounts are `BigDecimal`, and anything that would need rounding is rejected
- **Constant-time webhook verification** over the raw request bytes
- **Spring Boot 3.5 and 4**: auto-configuration, `epay.*` properties with IDE completion, and a
  verified `@EpayWebhook` controller parameter

| Artifact | Use it for |
| --- | --- |
| `com.epayethiopia:epay-java` | Any Java application |
| `com.epayethiopia:epay-spring-boot-starter` | Spring Boot applications. Includes `epay-java` |

## Install

Gradle:

```kotlin
dependencies {
    implementation("com.epayethiopia:epay-java:0.1.0")
    // or, in a Spring Boot application:
    implementation("com.epayethiopia:epay-spring-boot-starter:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>com.epayethiopia</groupId>
  <artifactId>epay-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick start

```java
import com.epayethiopia.epay.Epay;
import com.epayethiopia.epay.InitializeParams;
import com.epayethiopia.epay.model.InitializedPayment;

Epay epay = Epay.builder()
        .apiKey(System.getenv("EPAY_SECRET_KEY"))
        .build();

InitializedPayment session = epay.payments().initialize(InitializeParams.builder()
        .amount("250.00")
        .currencyCode("ETB")
        .customerPhone("+251911234567")
        .merchantReference("order_123")
        .returnUrl("https://yourstore.com/orders/123/complete")
        .idempotencyKey("order_123")
        .build());

// Save session.reference(), then send the customer to pay.
response.sendRedirect(session.checkoutUrl());
```

The key prefix picks the environment: `sk_test_…` runs against the sandbox and `sk_live_…` moves
real money. There is no separate mode to configure.

The client is thread-safe. Build one and share it.

## Configuration

Every option falls back to an environment variable, so `Epay.fromEnvironment()` is enough.

| Builder method | Environment variable | Default |
| --- | --- | --- |
| `apiKey` | `EPAY_SECRET_KEY` | *required* |
| `webhookSecret` | `EPAY_WEBHOOK_SECRET` | none |
| `baseUrl` | `EPAY_BASE_URL` | `https://api.epayethiopia.com/v1` |
| `timeout` | none | 30 seconds per attempt |
| `maxRetries` | none | `2` |
| `header(name, value)` | none | none |

```java
Epay epay = Epay.builder()
        .timeout(Duration.ofSeconds(10))
        .maxRetries(3)
        .build();

epay.mode();       // Optional<Mode>: LIVE or SANDBOX
epay.isSandbox();  // boolean
```

`epay.toString()` masks the key, so a client is safe to log.

### Bring your own HTTP client

Pass a `java.net.http.HttpClient` for an outbound proxy, mTLS, or a custom executor:

```java
HttpClient http = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 8080)))
        .sslContext(mutualTlsContext)
        .build();

Epay epay = Epay.builder().httpClient(http).build();
```

Leave redirects off, which is the `HttpClient` default. Following a redirect would resend your API
key to wherever it points.

To route requests through something else entirely, such as OkHttp or Spring's `RestClient`,
implement the one-method `EpayHttpClient` interface.

## Payments

### Initialize

```java
InitializedPayment session = epay.payments().initialize(InitializeParams.builder()
        .amount(new BigDecimal("250"))     // or "250", or .amountMinorUnits(25_000)
        .currencyCode("etb")               // uppercased
        .customerPhone("0911234567")       // rewritten to +251911234567
        .merchantReference("order_123")
        .email("abebe@example.com")
        .callbackUrl("https://yourstore.com/webhooks/epay") // overrides the dashboard URL
        .idempotencyKey("order_123")
        .build());
```

`build()` validates every field, so a typo throws `EpayValidationException` immediately instead of
costing a round trip.

**On amounts.** Pass a `String`, a `BigDecimal`, or a whole number of cents. The result always
carries two decimal places. A `BigDecimal` with more than two significant decimal places, such as
`250.005`, is rejected rather than rounded, because silently changing an amount is worse than
failing.

**On idempotency keys.** If you omit `idempotencyKey`, the SDK generates a fresh one per call. That
makes its internal retries safe (a retried `initialize` cannot double-charge), but it does *not*
deduplicate across separate calls. Pass your own order id to get that guarantee.

### Verify before fulfilling

```java
VerifiedPayment receipt = epay.payments().verify(reference);
receipt.status();         // COMPLETED
receipt.amount();         // BigDecimal 250.00
receipt.paymentMethod();  // "telebirr"
receipt.customer().phone();
```

The API rejects a transaction that is not yet `completed`, so use `transactions().retrieve(…)` first
if you would rather branch on status than catch an `EpayBadRequestException`.

### Cancel

```java
epay.payments().cancel(reference);
```

Only `pending` and `processing` transactions can be cancelled, and cancellation is irreversible.
This call is never retried automatically, because the endpoint takes no idempotency key.

## Transactions

```java
Transaction transaction = epay.transactions().retrieve(reference);

for (TimelineEvent event : epay.transactions().timeline(reference).events()) {
    System.out.println(event.eventType() + " " + event.occurredAt());
}
```

### Listing and pagination

The endpoint is cursor-paginated at a fixed 10 per page. Iterate a page to walk every following
page, fetching lazily and carrying your filters along:

```java
ListParams filters = ListParams.builder()
        .status(TransactionStatus.COMPLETED)
        .currency("ETB")
        .from(LocalDate.of(2026, 8, 1))
        .to(LocalDate.of(2026, 8, 31))
        .build();

for (Transaction transaction : epay.transactions().list(filters)) {
    System.out.println(transaction.reference() + " " + transaction.amount());
}
```

The API caps the range at 90 days.

Other ways to consume the same endpoint:

```java
TransactionPage page = epay.transactions().list();
page.data();       // exactly this page, no extra requests
page.hasMore();    // boolean
page.nextPage();   // Optional<TransactionPage>

List<Transaction> recent = epay.transactions().list().toList(50);  // cap the walk

epay.transactions().list().stream()      // lazy: stops fetching once satisfied
        .filter(t -> t.amount().compareTo(threshold) > 0)
        .findFirst();

for (TransactionPage each : epay.transactions().list().pages()) {  // page at a time
    processBatch(each.data());
}
```

## Payment providers

```java
epay.paymentProviders().list();         // compact, for dropdowns
epay.paymentProviders().getAll();       // with category and enabled state
epay.paymentProviders().listEnabled();  // only what you can route to today
```

Both endpoints are mode-aware and permission-gated: `list` needs `list_platform_payment_provider`,
and `getAll` needs `get_platform_payment_provider`.

## Webhooks

Verify the `X-Epay-Signature` header against the **raw** request bytes before you trust a payload.
Parsing the JSON and serializing it again can reorder keys and change whitespace, which changes the
digest and rejects valid deliveries.

```java
byte[] raw = request.getInputStream().readAllBytes();
String signature = request.getHeader(Webhooks.SIGNATURE_HEADER);

WebhookEvent event;
try {
    event = epay.webhooks().constructEvent(raw, signature);
} catch (EpayWebhookSignatureException e) {
    response.setStatus(401);
    return;
}

queue.publish(event);   // acknowledge fast, process later
response.setStatus(200);
```

`constructEvent` fails closed: any event it returns had a valid signature. In Spring Boot, use
[`@EpayWebhook`](#webhooks-in-spring) instead of writing this yourself.

### Handling events

```java
switch (event.type()) {
    case PAYMENT_SUCCESS -> fulfil(event.reference());
    case PAYMENT_FAILED, PAYMENT_CANCELLED -> release(event.reference());
    case PAYMENT_REFUNDING, PAYMENT_REFUNDED, PAYMENT_REVERSED -> reconcile(event);
    case UNKNOWN -> log.info("Unhandled ePay event {}", event.eventName());
}
```

`UNKNOWN` covers events added to the API after this SDK version, so a new event cannot break your
handler. `event.raw()` holds the full payload, including any field the SDK does not model yet.

ePay retries anything that is not a `2xx` within 10 seconds, up to 5 attempts, so keep the handler
fast and make it idempotent: deduplicate on `event.reference()`.

## Spring Boot

Add `epay-spring-boot-starter` and configure it in `application.yml`:

```yaml
epay:
  api-key: ${EPAY_SECRET_KEY}
  webhook-secret: ${EPAY_WEBHOOK_SECRET}
  # base-url: https://api.epayethiopia.com/v1
  # timeout: 30s
  # max-retries: 2
```

Then inject the client anywhere:

```java
@RestController
class CheckoutController {
    private final Epay epay;

    CheckoutController(Epay epay) {
        this.epay = epay;
    }

    @PostMapping("/orders/{id}/pay")
    ResponseEntity<Void> pay(@PathVariable String id) {
        Order order = orders.find(id);
        InitializedPayment session = epay.payments().initialize(InitializeParams.builder()
                .amount(order.total())
                .currencyCode("ETB")
                .customerPhone(order.phone())
                .merchantReference(order.id())
                .idempotencyKey(order.id())
                .build());

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(session.checkoutUrl()))
                .build();
    }
}
```

| Property | Default |
| --- | --- |
| `epay.enabled` | `true` |
| `epay.api-key` | `EPAY_SECRET_KEY` |
| `epay.webhook-secret` | `EPAY_WEBHOOK_SECRET` |
| `epay.base-url` | `EPAY_BASE_URL`, then production |
| `epay.timeout` | `30s` |
| `epay.max-retries` | `2` |
| `epay.headers.*` | none |

A missing or invalid key stops the application at startup with a message saying what to set. Set
`epay.enabled=false` to start without the client, for example in tests that do not need it.

To adjust the client, declare an `EpayClientCustomizer` bean. To replace it, declare your own `Epay`
bean and the auto-configuration steps aside. An `EpayHttpClient` bean, if present, is used as the
transport.

### Webhooks in Spring

Annotate a `WebhookEvent` parameter with `@EpayWebhook`:

```java
@RestController
class EpayWebhookController {

    @PostMapping("/webhooks/epay")
    ResponseEntity<Void> handle(@EpayWebhook WebhookEvent event) {
        queue.publish(event);   // acknowledge fast, process later
        return ResponseEntity.ok().build();
    }
}
```

The signature is checked against the raw request bytes before your method runs. A missing or wrong
signature answers `401`, and a signed body that is not a JSON object answers `400`, so every event
your method receives is genuine. Do not add `@RequestBody` to the same method: it consumes the body
before it can be verified.

This works alongside request-logging filters that wrap the request in a
`ContentCachingRequestWrapper`, even if they read the body first.

If you use Spring Security, ePay sends no CSRF token and no credentials, so open the route and
exempt it from CSRF protection. The signature is what authenticates the request:

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/webhooks/epay").permitAll()
        .anyRequest().authenticated())
    .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/epay"));
```

`@EpayWebhook` supports Spring MVC. In WebFlux, read the body as a `byte[]` and call
`epay.webhooks().constructEvent(…)` yourself.

## Error handling

Every failure extends the unchecked `EpayException`. HTTP failures carry the status, the parsed
body, and the response headers.

```java
try {
    VerifiedPayment receipt = epay.payments().verify(reference);
} catch (EpayNotFoundException e) {
    return Optional.empty();
} catch (EpayBadRequestException e) {
    return notSettledYet();
} catch (EpayRateLimitException e) {
    scheduleRetry(e.retryAfter().orElse(Duration.ofSeconds(1)));
    throw e;
} catch (EpayApiException e) {
    log.error("ePay failure {} {} {}", e.status(), e.request(), e.body());
    throw e;
}
```

| Exception | Thrown when |
| --- | --- |
| `EpayValidationException` | A value failed local validation; no request was sent |
| `EpayConfigException` | The client was built with unusable options |
| `EpayBadRequestException` | `400` |
| `EpayAuthenticationException` | `401`: key missing, invalid, or revoked |
| `EpayPermissionDeniedException` | `403`: IP not whitelisted, or key lacks a permission |
| `EpayNotFoundException` | `404` |
| `EpayConflictException` | `409` |
| `EpayUnprocessableEntityException` | `422` |
| `EpayRateLimitException` | `429`: exposes `retryAfter()` |
| `EpayServerException` | `5xx` |
| `EpayTimeoutException` | The attempt exceeded `timeout` |
| `EpayConnectionException` | No response was received at all |
| `EpayWebhookSignatureException` | A webhook signature was missing or wrong |

`408`, `409`, `429`, `5xx`, and network errors are retried automatically before surfacing.

## Sandbox testing

Use a `sk_test_…` key with the documented magic phone numbers:

| Phone | OTP | Outcome |
| --- | --- | --- |
| `251900000000` | `000111` | Generic sandbox account |
| `251900000001` | `123456` | Completes, fires `payment.success` |
| `251900000002` | `654321` | `INVALID_OTP` |
| `251900000003` | `111111` | `OTP_EXPIRED` |
| `251900000004` | none | Declined, fires `payment.failed` |

Generate a fresh idempotency key per test run: reusing one returns the cached response instead of
triggering the scenario again.

### Testing your own code

`EpayHttpClient` is a functional interface, so a lambda is enough to script responses and no request
leaves the process:

```java
Epay epay = Epay.builder()
        .apiKey("sk_test_unit_tests_only")
        .httpClient(request -> EpayHttpResponse.of(200, """
                {"reference":"P1","checkoutUrl":"https://checkout.example/P1","status":"success"}
                """))
        .build();
```

To generate webhook fixtures, sign them with the same secret your code under test uses:

```java
String signature = Webhooks.withSecret("whsec_test").sign(payload);
```

## Unmodelled endpoints

`epay.request(…)` reaches anything this version does not wrap yet, with the same auth, timeout,
retry, and error handling:

```java
Object data = epay.request("GET", "/some/new/endpoint", Map.of("limit", "10"), null);
```

## Development

Requires JDK 17 or newer.

```sh
./gradlew build                  # compile with -Werror, test, and javadoc
./gradlew :epay-spring-boot-starter:test -PspringBootVersion=4.1.1
./gradlew publishToMavenLocal    # try the artifacts in another project
```

## Releasing

CI builds and tests on Java 17, 21, and 25, and tests the starter against Spring Boot 3.5 and 4.1.
It also checks that `gradle.properties` and `Epay.VERSION` agree.

One-time setup:

1. Sign in at [central.sonatype.com](https://central.sonatype.com) and verify the
   `com.epayethiopia` namespace with the DNS TXT record it gives you for `epayethiopia.com`.
2. Under **View Account → Generate User Token**, create a token. Its two halves are
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`, not your login.
3. Create a signing key and publish its public half, which Central checks signatures against:

   ```sh
   gpg --quick-gen-key "ePay <dev@epayethiopia.com>" rsa4096 sign 3y
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID>   # the value of SIGNING_KEY
   ```

4. In the repository, create an environment named `maven-central` holding `MAVEN_CENTRAL_USERNAME`,
   `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, and `SIGNING_KEY_PASSWORD`.

To release, bump `VERSION_NAME` in `gradle.properties` and `Epay.VERSION` together, then:

```sh
git tag v0.2.0 && git push origin v0.2.0
```

Both artifacts always release together at one version. A Maven Central version can never be
deleted or replaced, so `release.yml` runs the full build and checks the tag against both version
declarations before anything is uploaded.

## License

MIT
