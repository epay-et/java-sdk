package com.epayethiopia.epay.http;

import java.io.IOException;

/**
 * The transport the SDK sends requests through.
 *
 * <p>The default, {@link JdkEpayHttpClient}, uses {@code java.net.http} and needs no
 * dependencies. Implement this to route requests through a client your
 * application already runs, or to script responses in your own tests.
 *
 * <p>Return every response, whatever its status: the SDK maps non-2xx statuses
 * and decides what to retry, so the error hierarchy and retry policy stay in one
 * place. Throw only when no response was received.
 */
@FunctionalInterface
public interface EpayHttpClient {

    /**
     * Sends one attempt.
     *
     * @throws java.net.http.HttpTimeoutException or {@link java.net.SocketTimeoutException}
     *     when the attempt exceeded {@link EpayHttpRequest#timeout()}, which the SDK
     *     reports as a timeout rather than a connection failure
     * @throws IOException when no response was received for any other reason
     * @throws InterruptedException if the calling thread was interrupted
     */
    EpayHttpResponse send(EpayHttpRequest request) throws IOException, InterruptedException;
}
