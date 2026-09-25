package com.epayethiopia.epay.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * The default transport, on {@code java.net.http}.
 *
 * <p>Pass your own {@link HttpClient} to configure a proxy, an {@code SSLContext}
 * for mTLS, or an executor. Leave redirects at the default of
 * {@link HttpClient.Redirect#NEVER}: following one would resend the API key to
 * wherever the redirect points.
 */
public final class JdkEpayHttpClient implements EpayHttpClient {

    private final HttpClient client;

    public JdkEpayHttpClient(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /** A client with the given connect timeout, which never follows redirects. */
    public static JdkEpayHttpClient create(Duration connectTimeout) {
        return new JdkEpayHttpClient(HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    @Override
    public EpayHttpResponse send(EpayHttpRequest request) throws IOException, InterruptedException {
        byte[] body = request.body();
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout())
                .method(request.method(), body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        request.headers().forEach(builder::header);

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new EpayHttpResponse(response.statusCode(), response.headers().map(), response.body());
    }
}
