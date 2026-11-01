package com.tripwise.dto;

import java.time.Instant;

/**
 * @param token                 short-lived access JWT
 * @param refreshToken          opaque rotating refresh token; exchange at POST /api/v1/auth/refresh
 * @param refreshTokenExpiresAt when {@code refreshToken} stops working
 */
public record AuthResponse(String token, String email, String fullName, Long userId, String refreshToken,
                           Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "AuthResponse[token=***, email=" + email + ", userId=" + userId + ", refreshToken=***]";
    }
}
