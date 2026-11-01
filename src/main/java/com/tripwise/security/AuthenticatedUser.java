package com.tripwise.security;

/**
 * Principal for requests authenticated with a JWT. Controllers obtain it via {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(Long id, String email) {
}
