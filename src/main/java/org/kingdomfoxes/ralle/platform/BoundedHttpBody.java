package org.kingdomfoxes.ralle.platform;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** Bounded, nonblocking response collection with a deadline through the final body byte. */
public final class BoundedHttpBody {
    private BoundedHttpBody() {}

    public static CompletableFuture<HttpResponse<String>> send(HttpClient client, HttpRequest request, int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("Invalid HTTP body limit");
        var exchange = client.sendAsync(request, ignored -> new Subscriber(maximumBytes));
        // Keep the transport future separate: completing it with orTimeout would prevent
        // cancel(true) from aborting an exchange whose headers arrived but body stalled.
        var result = new CompletableFuture<HttpResponse<String>>();
        exchange.whenComplete((response, failure) -> {
            if (failure == null) result.complete(response);
            else result.completeExceptionally(failure);
        });
        result.orTimeout(request.timeout().orElse(Duration.ofSeconds(12)).toNanos(), TimeUnit.NANOSECONDS);
        result.whenComplete((response, failure) -> {
            if (failure != null) exchange.cancel(true);
        });
        return result;
    }

    public static final class BodyLimitException extends IOException {
        BodyLimitException() { super("HTTP response exceeds the document limit"); }
    }

    static final class Subscriber implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofByteArray(), bytes -> new String(bytes, StandardCharsets.UTF_8));
        private final int maximumBytes;
        private int received;
        private Flow.Subscription subscription;
        private boolean finished;

        Subscriber(int maximumBytes) { this.maximumBytes = maximumBytes; }
        @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (finished) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > maximumBytes - received) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new BodyLimitException());
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable failure) {
            if (finished) return;
            finished = true;
            delegate.onError(failure);
        }
        @Override public void onComplete() {
            if (finished) return;
            finished = true;
            delegate.onComplete();
        }
    }
}
