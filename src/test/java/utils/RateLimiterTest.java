package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final long JANELA = 60_000L;

    @Test
    void bloqueiaAposAtingirOLimiteDentroDaJanela() {
        RateLimiter limiter = new RateLimiter(3, JANELA);
        long t = 1_000_000L;
        assertTrue(limiter.tryAcquire("1.2.3.4", t));
        assertTrue(limiter.tryAcquire("1.2.3.4", t + 1));
        assertTrue(limiter.tryAcquire("1.2.3.4", t + 2));
        assertFalse(limiter.tryAcquire("1.2.3.4", t + 3));
    }

    @Test
    void liberaNovamenteQuandoAJanelaPassa() {
        RateLimiter limiter = new RateLimiter(1, JANELA);
        long t = 5_000_000L;
        assertTrue(limiter.tryAcquire("a@b.com", t));
        assertFalse(limiter.tryAcquire("a@b.com", t + JANELA - 1));
        assertTrue(limiter.tryAcquire("a@b.com", t + JANELA));
    }

    @Test
    void chavesSaoIndependentesEInsensiveisAMaiusculas() {
        RateLimiter limiter = new RateLimiter(1, JANELA);
        assertTrue(limiter.tryAcquire("Maria@Hospital.local", 0));
        assertFalse(limiter.tryAcquire("maria@hospital.local", 1));
        assertTrue(limiter.tryAcquire("joao@hospital.local", 1));
    }

    @Test
    void rejeitaConfiguracaoInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, JANELA));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, 0));
    }
}
