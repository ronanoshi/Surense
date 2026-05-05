package com.surense.infra.ratelimit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

/**
 * Confirms that the {@code surense.rate-limit.*} block in
 * {@code application.yml} binds onto {@link RateLimitProperties} with the
 * documented defaults. If someone tweaks a number in YAML these expectations
 * will fail and force a deliberate review.
 */
@SpringBootTest(classes = RateLimitPropertiesTest.Config.class)
class RateLimitPropertiesTest {

    @EnableConfigurationProperties(RateLimitProperties.class)
    static class Config {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private RateLimitProperties props;

    @Test
    void cacheCap_defaultsTo100k() {
        Assertions.assertThat(props.cacheMaxEntries()).isEqualTo(100_000);
    }

    @Test
    void cacheExpireAfterAccess_defaultsToOneHour() {
        Assertions.assertThat(props.cacheExpireAfterAccess()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void enabled_defaultsToTrueInDevProfile() {
        Assertions.assertThat(props.enabled()).isTrue();
    }

    @Test
    void exemptPaths_defaultIncludesHealthProbes() {
        Assertions.assertThat(props.exemptPaths())
                .containsExactlyInAnyOrder("/actuator/health", "/actuator/health/**");
    }

    @Test
    void unauthIp_defaultIs30PerMinute() {
        Assertions.assertThat(props.unauthIp().capacity()).isEqualTo(30);
        Assertions.assertThat(props.unauthIp().refill()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void authUser_defaultIs60PerMinute() {
        Assertions.assertThat(props.authUser().capacity()).isEqualTo(60);
        Assertions.assertThat(props.authUser().refill()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void login_defaultIs5Per15Minutes() {
        Assertions.assertThat(props.login().capacity()).isEqualTo(5);
        Assertions.assertThat(props.login().refill()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void refresh_defaultIs10PerMinute() {
        Assertions.assertThat(props.refresh().capacity()).isEqualTo(10);
        Assertions.assertThat(props.refresh().refill()).isEqualTo(Duration.ofMinutes(1));
    }

    /**
     * Companion test that overrides a property and asserts it bubbles through —
     * proves YAML values aren't being silently shadowed by code defaults.
     */
    @SpringBootTest(classes = RateLimitPropertiesTest.Config.class)
    @TestPropertySource(properties = {
            "surense.rate-limit.unauth-ip.capacity=7",
            "surense.rate-limit.unauth-ip.refill=PT2M"
    })
    static class OverrideTest {

        @org.springframework.beans.factory.annotation.Autowired
        private RateLimitProperties overridden;

        @Test
        void overriddenValuesWin() {
            Assertions.assertThat(overridden.unauthIp().capacity()).isEqualTo(7);
            Assertions.assertThat(overridden.unauthIp().refill()).isEqualTo(Duration.ofMinutes(2));
        }
    }
}
