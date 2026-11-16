package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final long WINDOW = 60_000L;

    @Test
    void blocksAfterReachingLimitWithinWindow() {
        RateLimiter limiter = new RateLimiter(3, WINDOW);
        long t = 1_000_000L;
        assertTrue(limiter.tryAcquire("1.2.3.4", t));
        assertTrue(limiter.tryAcquire("1.2.3.4", t + 1));
        assertTrue(limiter.tryAcquire("1.2.3.4", t + 2));
        assertFalse(limiter.tryAcquire("1.2.3.4", t + 3));
    }

    @Test
    void allowsAgainWhenWindowPasses() {
        RateLimiter limiter = new RateLimiter(1, WINDOW);
        long t = 5_000_000L;
        assertTrue(limiter.tryAcquire("a@b.com", t));
        assertFalse(limiter.tryAcquire("a@b.com", t + WINDOW - 1));
        assertTrue(limiter.tryAcquire("a@b.com", t + WINDOW));
    }

    @Test
    void keysAreIndependentAndCaseInsensitive() {
        RateLimiter limiter = new RateLimiter(1, WINDOW);
        assertTrue(limiter.tryAcquire("Maria@Hospital.local", 0));
        assertFalse(limiter.tryAcquire("maria@hospital.local", 1));
        assertTrue(limiter.tryAcquire("joao@hospital.local", 1));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, WINDOW));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, 0));
    }
}
