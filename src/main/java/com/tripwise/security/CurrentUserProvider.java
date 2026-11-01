package com.tripwise.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user from the SecurityContext (populated by {@link JwtAuthenticationFilter}).
 * This is the only source of the user id for ownership checks; never take it from request input.
 */
@Component
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
}
