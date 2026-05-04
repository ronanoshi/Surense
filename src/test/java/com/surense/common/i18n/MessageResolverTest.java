package com.surense.common.i18n;

import com.surense.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessageResolverTest {

    private MessageResolver resolver;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(false);
        resolver = new MessageResolver(ms);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesKnownKeyWithoutArgs() {
        assertThat(resolver.resolve("error.auth.badCredentials"))
                .isEqualTo("Invalid username or password");
    }

    @Test
    void resolvesKnownKeyWithSubstitutedArgs() {
        assertThat(resolver.resolve("error.user.notFound", 42))
                .isEqualTo("User with id 42 was not found");
    }

    @Test
    void fallsBackToKeyWhenMessageMissing() {
        String unknownKey = "error.absolutely.does.not.exist";

        assertThat(resolver.resolve(unknownKey)).isEqualTo(unknownKey);
    }

    @Test
    void resolvesByErrorCode() {
        assertThat(resolver.resolve(ErrorCode.NOT_IMPLEMENTED))
                .isEqualTo("This endpoint is not yet implemented");
    }

    @Test
    void resolvesByErrorCodeWithArgs() {
        assertThat(resolver.resolve(ErrorCode.TICKET_NOT_FOUND, 7))
                .isEqualTo("Ticket with id 7 was not found");
    }
}
