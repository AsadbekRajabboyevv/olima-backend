package com.olima.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.List;
import org.springframework.web.util.pattern.PathPattern;

record CompiledRule(
    RateLimitProperties.Rule rule,
    List<PathPattern> patterns,
    Cache<String, TokenBucket> buckets) {}
