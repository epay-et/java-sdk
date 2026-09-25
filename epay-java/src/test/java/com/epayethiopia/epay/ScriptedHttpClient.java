package com.epayethiopia.epay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.epayethiopia.epay.http.EpayHttpClient;
import com.epayethiopia.epay.http.EpayHttpRequest;
import com.epayethiopia.epay.http.EpayHttpResponse;

/** Replays queued responses and records every request, so no test touches the network. */
final class ScriptedHttpClient implements EpayHttpClient {

    @FunctionalInterface
    interface Step {
        EpayHttpResponse respond(EpayHttpRequest request) throws IOException, InterruptedException;
    }

    private final Deque<Step> steps = new ArrayDeque<>();
    final List<EpayHttpRequest> requests = new ArrayList<>();

    ScriptedHttpClient reply(int status, String body) {
        steps.add(request -> EpayHttpResponse.of(status, body));
        return this;
    }

    ScriptedHttpClient reply(int status, String body, Map<String, List<String>> headers) {
        steps.add(request -> new EpayHttpResponse(status, headers, body.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    ScriptedHttpClient fail(IOException error) {
        steps.add(request -> {
            throw error;
        });
        return this;
    }

    ScriptedHttpClient then(Step step) {
        steps.add(step);
        return this;
    }

    @Override
    public EpayHttpResponse send(EpayHttpRequest request) throws IOException, InterruptedException {
        requests.add(request);
        Step step = steps.poll();
        if (step == null) {
            throw new AssertionError("Unexpected request: " + request);
        }
        return step.respond(request);
    }

    EpayHttpRequest last() {
        return requests.get(requests.size() - 1);
    }

    static String bodyOf(EpayHttpRequest request) {
        byte[] body = request.body();
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }
}
