package com.surense.infra.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the in-process rate-limit framework (Step 4b).
 *
 * <p>The actual default values for each limit are kept in
 * {@code src/main/resources/application.yml} so operators can tune them in one
 * place without recompiling. The {@link DefaultValue} annotations on
 * {@code cacheMaxEntries}, {@code cacheExpireAfterAccess},
 * {@code exemptPaths}, and {@code enabled} are fall-backs for unit tests that
 * don't load the YAML.
 *
 * <p>Limit conventions:
 * <ul>
 *   <li>{@link LimitSpec#capacity()} — max tokens the bucket can hold (also
 *       the burst size at t=0).</li>
 *   <li>{@link LimitSpec#refill()} — wall-clock duration over which a full
 *       capacity worth of tokens is replenished (greedy refill, i.e. tokens
 *       trickle back continuously rather than as a single batch).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "surense.rate-limit")
public record RateLimitProperties(

        /**
         * Maximum number of distinct rate-limit keys held in memory at once.
         * Caps worst-case heap growth from a unique-key spray attack.
         */
        @DefaultValue("100000") int cacheMaxEntries,

        /**
         * Evict a bucket from the Caffeine store after it has not been accessed
         * for this duration. Independent of {@link #cacheMaxEntries()} — keeps
         * idle keys from pinning memory when the store is far below the size
         * cap. Tunable so long-running soak tests can shorten it without
         * recompiling.
         */
        @DefaultValue("PT1H") Duration cacheExpireAfterAccess,

        /**
         * Request paths exempted from the per-IP / per-user limiters. Ant-style
         * patterns (e.g. {@code /actuator/health/**}). Login / refresh
         * limiters are unaffected — they only run on their dedicated paths.
         */
        @DefaultValue({"/actuator/health", "/actuator/health/**"})
        List<String> exemptPaths,

        /**
         * Master switch for {@link IpRateLimitFilter} and {@link UserRateLimitFilter}.
         * Disabled in the {@code test} profile so unrelated {@code @SpringBootTest}
         * classes do not share exhausted buckets with the rate-limit integration
         * suite (single {@link BucketProvider} bean per context).
         */
        @DefaultValue("true") boolean enabled,

        /** Per-IP limit for unauthenticated requests. */
        LimitSpec unauthIp,

        /** Per-userId limit for authenticated requests. */
        LimitSpec authUser,

        /** Per-(username + clientIp) limit on failed login attempts. */
        LimitSpec login,

        /** Per-refresh-token limit (keyed on a SHA-256 hash of the token). */
        LimitSpec refresh) {

    /**
     * A single token-bucket configuration: how many tokens fit and how fast
     * they refill. Both fields must be supplied — there is no in-code default
     * here on purpose, so a missing YAML entry surfaces as a startup error
     * rather than silently using a number nobody chose.
     */
    public record LimitSpec(int capacity, Duration refill) {}
}
