package com.interview.employees.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.function.Supplier;

public class TtlCache<K, V> {

    private final long ttlNanos;
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();

    public TtlCache(Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("TTL must be positive");
        this.ttlNanos = ttl.toNanos();
    }

    public V get(K key, Supplier<V> loader) {
        long now = System.nanoTime();
        Entry<V> cached = entries.get(key);
        if (cached != null && cached.expiresAt > now) return cached.value;
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            now = System.nanoTime();
            cached = entries.get(key);
            if (cached != null && cached.expiresAt > now) return cached.value;
            V fresh = loader.get();
            entries.put(key, new Entry<>(fresh, System.nanoTime() + ttlNanos));
            return fresh;
        }
    }

    public void invalidate(K key) { entries.remove(key); }
    private record Entry<V>(V value, long expiresAt) { }
}
