package com.docling.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Configurable retry policy for operations that may fail transiently.
 * Uses exponential backoff with jitter to avoid thundering herd problems.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *     .maxAttempts(5)
 *     .initialDelay(Duration.ofSeconds(1))
 *     .maxDelay(Duration.ofSeconds(30))
 *     .build();
 *
 * String result = policy.execute(() -> {
 *     // Your operation here
 *     return apiClient.makeRequest();
 * });
 * }</pre>
 */
public class RetryPolicy {

    private static final Logger log = LogManager.getLogger(RetryPolicy.class);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;
    private final double jitterFactor;
    private final Predicate<Throwable> retryPredicate;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.jitterFactor = builder.jitterFactor;
        this.retryPredicate = builder.retryPredicate;
    }

    /**
     * Creates a default retry policy suitable for most Docling operations.
     * - 4 attempts (initial + 3 retries)
     * - 1s initial delay, 30s max delay
     * - Exponential backoff with 2x multiplier
     * - 20% jitter
     * - Retries on network errors and 5xx responses
     */
    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    /**
     * Creates a retry policy with no retries (attempts once, fails immediately).
     */
    public static RetryPolicy noRetry() {
        return builder().maxAttempts(1).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the given operation with retry logic.
     *
     * @param operation The operation to execute
     * @param <T>       Return type
     * @return The result of the operation
     * @throws RuntimeException if all retry attempts are exhausted
     */
    public <T> T execute(RetryableOperation<T> operation) {
        Exception lastException = null;
        long delayMillis = initialDelay.toMillis();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1) {
                    log.debug("Retry attempt {} of {}", attempt, maxAttempts);
                }
                return operation.execute();
            } catch (InterruptedException e) {
                // Don't retry on interruption
                Thread.currentThread().interrupt();
                throw new DoclingClientException("Operation interrupted", e);
            } catch (Exception e) {
                lastException = e;

                // Check if we should retry this exception
                if (!shouldRetry(e)) {
                    log.debug("Exception not retryable: {}", e.getClass().getSimpleName());
                    throw wrapException(e);
                }

                // Don't sleep after the last attempt
                if (attempt < maxAttempts) {
                    long actualDelay = applyJitter(delayMillis);
                    log.warn("Attempt {} failed: {}. Retrying in {}ms...",
                            attempt, e.getMessage(), actualDelay);
                    try {
                        Thread.sleep(actualDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DoclingClientException("Interrupted during retry backoff", ie);
                    }

                    // Calculate next delay with exponential backoff
                    delayMillis = Math.min(
                            (long) (delayMillis * backoffMultiplier),
                            maxDelay.toMillis()
                    );
                } else {
                    log.error("All {} retry attempts exhausted", maxAttempts);
                }
            }
        }

        // All retries exhausted
        throw wrapException(lastException);
    }

    private boolean shouldRetry(Exception e) {
        return retryPredicate.test(e);
    }

    private long applyJitter(long delayMillis) {
        if (jitterFactor <= 0) {
            return delayMillis;
        }
        double jitter = (Math.random() * 2 - 1) * jitterFactor; // Random value in [-jitterFactor, +jitterFactor]
        return Math.max(0, (long) (delayMillis * (1 + jitter)));
    }

    private RuntimeException wrapException(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new DoclingClientException("Operation failed after " + maxAttempts + " attempts", e);
    }

    /**
     * Functional interface for retryable operations.
     *
     * @param <T> Return type
     */
    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    public static class Builder {
        private int maxAttempts = 4; // 1 initial + 3 retries
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double backoffMultiplier = 2.0;
        private double jitterFactor = 0.2; // 20% jitter
        private Predicate<Throwable> retryPredicate = Builder::defaultRetryPredicate;

        /**
         * Default retry predicate:
         * - Retry on IOException (network errors)
         * - Retry on HttpTimeoutException
         * - Retry on DoclingNetworkException
         * - Retry on DoclingHttpException with 5xx status
         * - Don't retry on InterruptedException (caller handles)
         * - Don't retry on 4xx errors (client errors)
         */
        private static boolean defaultRetryPredicate(Throwable e) {
            // Network-level failures
            if (e instanceof IOException || e instanceof HttpTimeoutException) {
                return true;
            }

            // Our custom network exception
            if (e instanceof DoclingNetworkException) {
                return true;
            }

            // HTTP errors: retry only on server errors (5xx)
            if (e instanceof DoclingHttpException) {
                return ((DoclingHttpException) e).isServerError();
            }

            // Don't retry other exceptions
            return false;
        }

        /**
         * Sets the maximum number of attempts (initial attempt + retries).
         * Must be at least 1.
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the initial delay before the first retry.
         */
        public Builder initialDelay(Duration initialDelay) {
            if (initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay must be non-negative");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         * Sets the maximum delay between retries (caps exponential backoff).
         */
        public Builder maxDelay(Duration maxDelay) {
            if (maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay must be non-negative");
            }
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Sets the backoff multiplier for exponential backoff.
         * Default is 2.0 (doubles delay each retry).
         */
        public Builder backoffMultiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }
            this.backoffMultiplier = multiplier;
            return this;
        }

        /**
         * Sets the jitter factor to randomize delays.
         * 0.2 means +/- 20% jitter. Set to 0 to disable jitter.
         */
        public Builder jitterFactor(double jitterFactor) {
            if (jitterFactor < 0 || jitterFactor > 1) {
                throw new IllegalArgumentException("jitterFactor must be between 0 and 1");
            }
            this.jitterFactor = jitterFactor;
            return this;
        }

        /**
         * Sets a custom predicate to determine which exceptions should trigger retries.
         */
        public Builder retryOn(Predicate<Throwable> predicate) {
            this.retryPredicate = predicate != null ? predicate : Builder::defaultRetryPredicate;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
