package utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZTF_J2EE - RateLimiter
 *
 * In-memory sliding-window attempt limiter (e.g. "5 requests every 15 minutes"). Each key
 * (IP, e-mail, ...) keeps the instants of its latest attempts.
 *
 * Known limitation: the state lives in JVM memory; it resets with Tomcat and is not shared
 * between instances (several servers would need a database or a distributed cache).
 */
public class RateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis) {
        if (maxAttempts <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxAttempts e windowMillis devem ser positivos.");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
    }

    /** Records an attempt for the key and tells whether it is still within the limit. */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, System.currentTimeMillis());
    }

    /** Variant with an explicit clock, used by the tests. */
    public boolean tryAcquire(String key, long nowMillis) {
        Deque<Long> history = attempts.computeIfAbsent(normalize(key), k -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && nowMillis - history.peekFirst() >= windowMillis) {
                history.pollFirst();
            }
            if (history.size() >= maxAttempts) {
                return false;
            }
            history.addLast(nowMillis);
            return true;
        }
    }

    public void reset(String key) {
        attempts.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}
