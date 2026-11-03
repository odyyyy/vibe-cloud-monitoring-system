package com.monitoring.healthcheck.service;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Collects a response body while enforcing a hard byte limit.
 *
 * <p>The standard {@link HttpResponse.BodyHandlers#ofString()} and {@code ofByteArray()} buffer
 * an unbounded response, which would let a misbehaving target exhaust the scheduler's heap. This
 * subscriber cancels the subscription and fails with {@link BodyTooLargeException} as soon as the
 * limit is crossed. Bodies are decoded as UTF-8: they are only used for assertions and simple
 * token extraction, never persisted or returned to clients.
 */
final class BoundedStringBodySubscriber implements HttpResponse.BodySubscriber<String> {

    private final int maxBytes;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final CompletableFuture<String> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedStringBodySubscriber(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public CompletionStage<String> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
        if (body.isDone()) {
            return;
        }
        for (ByteBuffer item : items) {
            if (item.remaining() > maxBytes - buffer.size()) {
                if (subscription != null) {
                    subscription.cancel();
                }
                body.completeExceptionally(new BodyTooLargeException(maxBytes));
                return;
            }
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            buffer.write(bytes, 0, bytes.length);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        body.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        body.complete(buffer.toString(StandardCharsets.UTF_8));
    }

    static final class BodyTooLargeException extends RuntimeException {

        BodyTooLargeException(int maxBytes) {
            super("Response body exceeds " + maxBytes + " bytes");
        }
    }
}
