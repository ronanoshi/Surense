package com.surense.infra.i18n;

import com.surense.infra.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Thin facade over Spring's {@link MessageSource} that:
 * <ul>
 *   <li>Resolves keys against the request's locale (via
 *       {@link LocaleContextHolder}) — set by Spring's
 *       {@code AcceptHeaderLocaleResolver} from the {@code Accept-Language}
 *       header, falling back to the JVM default.</li>
 *   <li>Returns the raw key as the message when the key is unknown
 *       (instead of throwing {@code NoSuchMessageException}). This makes
 *       missing translations visible during development without breaking
 *       the response.</li>
 *   <li>Provides a convenience overload accepting an {@link ErrorCode}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    public String resolve(ErrorCode code, Object... args) {
        return resolve(code.getMessageKey(), args);
    }
}
