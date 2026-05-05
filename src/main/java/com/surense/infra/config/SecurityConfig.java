package com.surense.infra.config;

import com.surense.infra.ratelimit.BucketProvider;
import com.surense.infra.ratelimit.RateLimitProperties;
import com.surense.infra.ratelimit.RateLimitResponseWriter;
import com.surense.infra.ratelimit.UserRateLimitFilter;
import com.surense.infra.security.ApiAuthenticationEntryPoint;
import com.surense.infra.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Stateless JWT security for {@code /api/v1/**}; public auth endpoints under
 * {@code /api/v1/auth/**}. Rate limiting stays Option B: servlet IP filter
 * (registered elsewhere), {@link UserRateLimitFilter} after the anonymous
 * identity is finalized.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserRateLimitFilter userRateLimitFilter(RateLimitProperties props,
                                            BucketProvider bucketProvider,
                                            RateLimitResponseWriter rateLimitResponseWriter) {
        return new UserRateLimitFilter(props, bucketProvider, rateLimitResponseWriter);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtAuthenticationFilter,
                                    UserRateLimitFilter userRateLimitFilter,
                                    ApiAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(userRateLimitFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    /**
     * Suppresses Boot's generated default user; JWT replaces form login — this bean is never used.
     */
    @Bean
    UserDetailsService noopUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
