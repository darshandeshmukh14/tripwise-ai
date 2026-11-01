package com.tripwise.service;

import com.tripwise.config.JwtProperties;
import com.tripwise.entity.RefreshToken;
import com.tripwise.entity.RefreshToken.RevokeReason;
import com.tripwise.exception.InvalidRefreshTokenException;
import com.tripwise.repository.RefreshTokenRepository;
import com.tripwise.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens. Each login starts a family; every refresh revokes the presented token and issues
 * a new one in the same family. Presenting an already-rotated token (outside a short grace window for parallel
 * requests) is treated as theft and revokes the whole family.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * @param token raw value for the client; never stored or logged
     */
    public record IssuedRefreshToken(Long userId, String token, Instant expiresAt) {
    }

    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        return create(userId, UUID.randomUUID().toString(), Instant.now(clock));
    }

    /**
     * @throws InvalidRefreshTokenException if the token is unknown, expired, revoked or reused
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Long userId = current.getUser().getId();
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        if (current.getRevokedAt() != null) {
            boolean rotatedWithinGrace = current.getRevokeReason() == RevokeReason.ROTATED
                    && current.getRevokedAt().plus(jwtProperties.refreshReuseGrace()).isAfter(now)
                    && refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(current.getFamilyId());
            if (rotatedWithinGrace) {
                return create(userId, current.getFamilyId(), now);
            }
            if (current.getRevokeReason() == RevokeReason.ROTATED) {
                log.warn("Refresh token reuse detected for user id={}; revoking the session", userId);
                refreshTokenRepository.revokeFamily(current.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
            }
            throw new InvalidRefreshTokenException();
        }

        current.setRevokedAt(now);
        current.setRevokeReason(RevokeReason.ROTATED);
        return create(userId, current.getFamilyId(), now);
    }

    /**
     * Ends the session the token belongs to. Unknown tokens are ignored (logout is idempotent).
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, Instant.now(clock)));
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public int purgeExpired() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now(clock));
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
        return deleted;
    }

    private IssuedRefreshToken create(Long userId, String familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(userRepository.getReferenceById(userId));
        token.setFamilyId(familyId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(now.plus(jwtProperties.refreshExpiration()));
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(userId, raw, token.getExpiresAt());
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
