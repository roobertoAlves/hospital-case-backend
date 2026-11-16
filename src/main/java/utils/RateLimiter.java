package utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZTF_J2EE - RateLimiter
 *
 * Limitador de tentativas em memoria por janela deslizante (ex.: "5 pedidos a cada 15 minutos").
 * Cada chave (IP, e-mail, ...) guarda os instantes das ultimas tentativas.
 *
 * Limitacao conhecida: o estado vive na memoria da JVM; reinicia com o Tomcat e nao e
 * compartilhado entre instancias (para varios servidores, usar banco ou cache distribuido).
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

    /** Registra uma tentativa para a chave e informa se ela ainda esta dentro do limite. */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, System.currentTimeMillis());
    }

    /** Variante com relogio explicito, usada pelos testes. */
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
