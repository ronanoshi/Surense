package com.surense.infra.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for token-bucket allocation.
 *
 * <p>Backed by a Caffeine cache with a hard size cap (
 * {@link RateLimitProperties#cacheMaxEntries()}) so an attacker cannot grow
 * the heap unbounded by spraying unique keys (e.g. random IPv6 addresses or
 * random usernames). A second floor — {@link RateLimitProperties#cacheExpireAfterAccess()}
 * — keeps idle buckets from sitting around forever even when the size cap is far
 * from reached.
 *
 * <p>Buckets are built lazily on first access and reused thereafter. The
 * {@link TimeMeter} is constructor-injected so unit tests can drive bucket
 * refill deterministically without sleeping.
 */
@Component
public class BucketProvider {

    private final RateLimitProperties props;
    private final TimeMeter timeMeter;
    private final Cache<RateLimitKey, Bucket> buckets;

    public BucketProvider(RateLimitProperties props, TimeMeter timeMeter) {
        this.props = props;
        this.timeMeter = timeMeter;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(props.cacheMaxEntries())
                .expireAfterAccess(props.cacheExpireAfterAccess())
                .build();
    }

    /**
     * Returns the bucket for {@code key}, creating it on first access.
     * The returned bucket's capacity and refill rate are determined by the
     * {@link LimitType} on the key plus the matching {@code LimitSpec} on
     * {@link RateLimitProperties}.
     */
    public Bucket resolve(RateLimitKey key) {
        return buckets.get(key, this::buildBucket);
    }

    /** Test/diagnostic accessor — current entry count in the bucket store. */
    public long size() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket buildBucket(RateLimitKey key) {
        RateLimitProperties.LimitSpec spec = specFor(key.type());
        Bandwidth limit = Bandwidth.builder()
                .capacity(spec.capacity())
                .refillGreedy(spec.capacity(), spec.refill())
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private RateLimitProperties.LimitSpec specFor(LimitType type) {
        return switch (type) {
            case UNAUTH_IP -> props.unauthIp();
            case AUTH_USER -> props.authUser();
            case LOGIN     -> props.login();
            case REFRESH   -> props.refresh();
        };
    }
}
