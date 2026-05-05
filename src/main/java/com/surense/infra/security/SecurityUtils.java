package com.surense.infra.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Current JWT-backed principal from {@link org.springframework.security.core.context.SecurityContext}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal up)) {
            throw new AccessDeniedException("Authentication required");
        }
        return up;
    }
}
