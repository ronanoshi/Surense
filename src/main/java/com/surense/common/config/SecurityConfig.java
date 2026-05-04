package com.surense.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security configuration for the cross-cutting infrastructure phase.
 *
 * <p>This config deliberately allows all requests so the global error pipeline,
 * correlation-id filter, and the dev-only {@code BoomController} can be exercised
 * without authentication. It is REPLACED in Step 6 with the real JWT-based
 * filter chain.
 *
 * <p>It also disables CSRF (we are a stateless REST API), the auto-generated
 * HTTP Basic prompt and the form-login page (so the boot log no longer prints
 * a generated security password).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Empty user store, declared solely to prevent {@code UserDetailsServiceAutoConfiguration}
     * from creating an auto-generated user (which logs a "Using generated security password"
     * warning on every boot). Replaced in Step 6 with the real JPA-backed implementation.
     */
    @Bean
    UserDetailsService emptyUserDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
