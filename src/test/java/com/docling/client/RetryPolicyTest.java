package com.docling.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetryPolicy behavior.
 */
class RetryPolicyTest {

    @Test
    void defaultPolicyHasCorrectSettings() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertNotNull(policy);
    }

    @Test
    void noRetryPolicyAttemptsOnce() {
        RetryPolicy policy = RetryPolicy.noRetry();
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(DoclingClientException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Test failure");
            });
        });

        assertEquals(1, attempts.get(), "Should attempt exactly once with noRetry policy");
    }

    @Test
    void retriesOnIOException() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(DoclingClientException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Network error");
            });
        });

        assertEquals(3, attempts.get(), "Should retry 3 times on IOException");
    }

    @Test
    void succeedsOnSecondAttempt() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        String result = policy.execute(() -> {
            int count = attempts.incrementAndGet();
            if (count < 2) {
                throw new IOException("Transient failure");
            }
            return "Success";
        });

        assertEquals("Success", result);
        assertEquals(2, attempts.get(), "Should succeed on second attempt");
    }

    @Test
    void doesNotRetryOnInterruptedException() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(10))
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(DoclingClientException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new InterruptedException("Interrupted");
            });
        });

        assertEquals(1, attempts.get(), "Should not retry on InterruptedException");
        assertTrue(Thread.interrupted(), "Thread interrupted flag should be set");
    }

    @Test
    void doesNotRetryOn4xxHttpError() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(10))
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(DoclingHttpException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new DoclingHttpException("Bad request", 400, "POST", null, "Invalid input");
            });
        });

        assertEquals(1, attempts.get(), "Should not retry on 4xx errors");
    }

    @Test
    void retriesOn5xxHttpError() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(DoclingHttpException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new DoclingHttpException("Server error", 503, "POST", null, "Service unavailable");
            });
        });

        assertEquals(3, attempts.get(), "Should retry 3 times on 5xx errors");
    }

    @Test
    void customRetryPredicateWorks() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .retryOn(e -> e instanceof IllegalStateException)
                .build();

        AtomicInteger attempts = new AtomicInteger(0);

        // Should retry IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("Test");
            });
        });
        assertEquals(3, attempts.get());

        attempts.set(0);

        // Should not retry IOException with custom predicate
        assertThrows(DoclingClientException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Test");
            });
        });
        assertEquals(1, attempts.get());
    }

    @Test
    void builderValidatesMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().maxAttempts(0).build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().maxAttempts(-1).build();
        });
    }

    @Test
    void builderValidatesDelays() {
        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().initialDelay(Duration.ofSeconds(-1)).build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().maxDelay(Duration.ofSeconds(-1)).build();
        });
    }

    @Test
    void builderValidatesBackoffMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().backoffMultiplier(0.5).build();
        });

        assertDoesNotThrow(() -> {
            RetryPolicy.builder().backoffMultiplier(1.0).build();
        });
    }

    @Test
    void builderValidatesJitterFactor() {
        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().jitterFactor(-0.1).build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().jitterFactor(1.5).build();
        });

        assertDoesNotThrow(() -> {
            RetryPolicy.builder().jitterFactor(0.0).build();
        });

        assertDoesNotThrow(() -> {
            RetryPolicy.builder().jitterFactor(1.0).build();
        });
    }

    @Test
    void wrapsCheckedExceptions() {
        RetryPolicy policy = RetryPolicy.noRetry();

        Exception result = assertThrows(DoclingClientException.class, () -> {
            policy.execute(() -> {
                throw new Exception("Checked exception");
            });
        });

        assertNotNull(result.getCause());
        assertEquals("Checked exception", result.getCause().getMessage());
    }

    @Test
    void preservesRuntimeExceptions() {
        RetryPolicy policy = RetryPolicy.noRetry();

        IllegalArgumentException result = assertThrows(IllegalArgumentException.class, () -> {
            policy.execute(() -> {
                throw new IllegalArgumentException("Runtime exception");
            });
        });

        assertEquals("Runtime exception", result.getMessage());
    }
}
