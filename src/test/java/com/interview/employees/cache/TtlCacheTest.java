package com.interview.employees.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TtlCacheTest {

    @Test
    void shouldReturnCachedValue() {
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofMinutes(1));
        AtomicInteger loads = new AtomicInteger();
        assertEquals("value", cache.get("key", () -> { loads.incrementAndGet(); return "value"; }));
        assertEquals("value", cache.get("key", () -> { loads.incrementAndGet(); return "other"; }));
        assertEquals(1, loads.get());
    }

    @Test
    void shouldReloadAfterExpiry() throws InterruptedException {
        TtlCache<String, Integer> cache = new TtlCache<>(Duration.ofMillis(20));
        AtomicInteger loads = new AtomicInteger();
        cache.get("key", loads::incrementAndGet);
        Thread.sleep(40);
        assertEquals(2, cache.get("key", loads::incrementAndGet));
    }

    @Test
    void shouldPreventCacheStampede() throws Exception {
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofMinutes(1));
        AtomicInteger loads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var calls = java.util.stream.IntStream.range(0, 24).mapToObj(i -> executor.submit(() -> { start.await(); return cache.get("key", () -> { loads.incrementAndGet(); return "fresh"; }); })).toList();
            start.countDown();
            for (Future<String> call : calls) assertEquals("fresh", call.get());
            assertEquals(1, loads.get());
        } finally { executor.shutdownNow(); }
    }
}
