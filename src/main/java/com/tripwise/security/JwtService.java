package com.tripwise.security;

import com.tripwise.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class JwtService {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        // Fails fast at startup if the secret is not valid Base64 or shorter than 256 bits
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expirationMs = properties.expiration();
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Returns the authenticated user if the token is a valid, unexpired HS256 token signed with our key.
     */
    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            Long userId = claims.get(CLAIM_USER_ID, Long.class);
            String email = claims.get(CLAIM_EMAIL, String.class);
            if (userId == null || email == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, email));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
