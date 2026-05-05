package com.surense.infra.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the rate-limit framework.
 *
 * <p>Only declares the {@link TimeMeter} bean used by {@link BucketProvider}
 * — production uses {@link TimeMeter#SYSTEM_MILLISECONDS}, tests can override
 * by providing their own bean (e.g. for time-travel via a mock meter).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnMissingBean
    TimeMeter rateLimitTimeMeter() {
        return TimeMeter.SYSTEM_MILLISECONDS;
    }
}
