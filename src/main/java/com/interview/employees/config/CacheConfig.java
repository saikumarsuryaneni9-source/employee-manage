package com.interview.employees.config;

import com.interview.employees.cache.TtlCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.Map;

@Configuration
public class CacheConfig {
    @Bean
    TtlCache<String, Map<String, BigDecimal>> analyticsCache(@Value("${analytics.cache.ttl:60s}") Duration ttl) {
        return new TtlCache<>(ttl);
    }
}
