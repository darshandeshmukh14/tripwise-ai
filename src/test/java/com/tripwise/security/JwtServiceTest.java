package com.tripwise.security;

import com.tripwise.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "5i9MxOUwxetR+RjNEb1HE+3rsINhN4SVXSuTJXlFyYdUjlOJTAGgafRlbjtfQuAt67qYHeBDdUAgHpmYLUYpgA==";
    private static final String OTHER_SECRET =
            "q0fCwJ0m3Yt8Y1o8wJ3l2c8l0Kx0dGJ8b0V8n4w2Yt4bQx1o9m3Yt8Y1o8wJ3l2c8l0Kx0dGJ8b0V8n4w2Yt4bA==";

    private static JwtProperties properties(String secret, long expirationMs) {
        return new JwtProperties(secret, expirationMs, Duration.ofDays(7), Duration.ofSeconds(10));
    }

    private final JwtService jwtService = new JwtService(properties(SECRET, 60_000));

    @Test
    void generateToken_validToken_parsesUserIdAndEmail() {
        String token = jwtService.generateToken(42L, "alice@example.com");

        assertThat(jwtService.parseToken(token))
                .contains(new AuthenticatedUser(42L, "alice@example.com"));
    }

    @Test
    void generateToken_usesHs256() {
        String token = jwtService.generateToken(1L, "a@b.io");

        String alg = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).build()
                .parseSignedClaims(token).getHeader().getAlgorithm();
        assertThat(alg).isEqualTo("HS256");
    }

    @Test
    void parseToken_tamperedSignature_returnsEmpty() {
        String token = jwtService.generateToken(1L, "a@b.io");
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    void parseToken_signedWithOtherKey_returnsEmpty() {
        String token = new JwtService(properties(OTHER_SECRET, 60_000)).generateToken(1L, "a@b.io");

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_expired_returnsEmpty() {
        String token = new JwtService(properties(SECRET, -1_000)).generateToken(1L, "a@b.io");

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_otherHmacAlgorithmWithSameKey_returnsEmpty() {
        String token = Jwts.builder()
                .subject("1").claim("userId", 1L).claim("email", "a@b.io")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS512)
                .compact();

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_garbage_returnsEmpty() {
        assertThat(jwtService.parseToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseToken("")).isEmpty();
    }
}
