package dev.challenge.notifications.api;

import dev.challenge.notifications.domain.BatchResult;
import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.ProviderId;
import dev.challenge.notifications.domain.SendReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Future-based facade over the synchronous client. Thread-safe with a thread-safe delegate.
 * Capacity, execution and rejection belong to the executor; a batch submits all entries.
 * External executors may run inline, queue without bounds or share capacity with other work.
 */
public final class AsyncNotificationClient implements AutoCloseable {
    private final NotificationClient delegate;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    private AsyncNotificationClient(NotificationClient delegate, Executor executor, ExecutorService ownedExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownedExecutor = ownedExecutor;
    }

    /** Creates a lazily started pool of 4 workers, 64 queued tasks and AbortPolicy. */
    public static AsyncNotificationClient create(NotificationClient delegate) {
        Objects.requireNonNull(delegate, "delegate");
        var executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        return new AsyncNotificationClient(delegate, executor, executor);
    }

    /** Uses the caller's executor; never shuts it down, including when it is an ExecutorService. */
    public static AsyncNotificationClient using(NotificationClient delegate, Executor executor) {
        return new AsyncNotificationClient(delegate, executor, null);
    }

    /** Returns acceptance or an exceptional future, including rejection or facade closure. */
    public CompletableFuture<SendReceipt> sendAsync(Notification notification) {
        return submit(() -> delegate.send(notification));
    }

    /** Selects a provider for this attempt without changing the default. */
    public CompletableFuture<SendReceipt> sendAsync(Notification notification, ProviderId providerId) {
        return submit(() -> delegate.send(notification, providerId));
    }

    private CompletableFuture<SendReceipt> submit(Supplier<SendReceipt> attempt) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Async client is closed"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> Objects.requireNonNull(attempt.get(), "receipt"), executor);
        } catch (RuntimeException failure) {
            // execute() may reject synchronously. Preserve one future-based failure channel.
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * Submits concurrently and waits. Do not call from a worker of the same limited executor:
     * occupying that worker while waiting for tasks in the same pool can deadlock.
     */
    public List<BatchResult> sendBatch(List<? extends Notification> notifications) {
        return sendBatchAsync(notifications).join();
    }

    /**
     * Snapshots the list, submits each entry and gathers outcomes in input order.
     * Entry failures (including rejection and cancellation) become Failed values.
     * Cancelling the aggregate does not cancel child attempts or undo provider effects.
     * A null list is rejected synchronously; null entries follow the delegate's validation.
     */
    public CompletableFuture<List<BatchResult>> sendBatchAsync(List<? extends Notification> notifications) {
        if (notifications == null) {
            throw new NotificationValidationException("notifications", "REQUIRED");
        }
        var snapshot = new ArrayList<>(notifications);
        var results = new ArrayList<CompletableFuture<BatchResult>>(snapshot.size());
        for (Notification notification : snapshot) {
            results.add(sendAsync(notification).handle((receipt, failure) -> failure == null
                    ? new BatchResult.Accepted(receipt)
                    : new BatchResult.Failed(unwrap(failure))));
        }
        return CompletableFuture.allOf(results.toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> results.stream().map(CompletableFuture::join).toList());
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    /**
     * Stops new submissions and requests orderly shutdown only for the owned pool.
     * Nonblocking and idempotent: does not await, interrupt or cancel accepted attempts.
     * Submissions racing with close may be accepted or rejected by the executor.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }
}
