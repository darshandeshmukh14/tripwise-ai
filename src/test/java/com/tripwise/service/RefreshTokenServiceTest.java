package com.tripwise.service;

import com.tripwise.config.JwtProperties;
import com.tripwise.entity.RefreshToken;
import com.tripwise.entity.RefreshToken.RevokeReason;
import com.tripwise.entity.User;
import com.tripwise.exception.InvalidRefreshTokenException;
import com.tripwise.repository.RefreshTokenRepository;
import com.tripwise.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String FAMILY = "family-1";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("unused", 900_000, Duration.ofDays(7), Duration.ofSeconds(10));
        service = new RefreshTokenService(refreshTokenRepository, userRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(7L);
        lenient().when(userRepository.getReferenceById(7L)).thenReturn(user);
    }

    @Test
    void issue_storesOnlyHashWithSlidingExpiry() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(7L);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(issued.token()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.token()))
                .isNotEqualTo(issued.token());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void rotate_activeToken_revokesItAndIssuesNewTokenInSameFamily() {
        RefreshToken current = token(null, null);
        given(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("raw"))).willReturn(Optional.of(current));

        RefreshTokenService.IssuedRefreshToken issued = service.rotate("raw");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getRevokeReason()).isEqualTo(RevokeReason.ROTATED);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
        assertThat(issued.userId()).isEqualTo(7L);
        assertThat(issued.token()).isNotEqualTo("raw");
    }

    @Test
    void rotate_reusedTokenAfterGrace_revokesFamilyAndThrows() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(token(NOW.minusSeconds(60), RevokeReason.ROTATED)));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeFamily(FAMILY, RevokeReason.REUSE_DETECTED, NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_reusedTokenWithinGraceWhileSessionActive_issuesTokenWithoutRevoking() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(token(NOW.minusSeconds(2), RevokeReason.ROTATED)));
        given(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(FAMILY)).willReturn(true);

        assertThat(service.rotate("raw").userId()).isEqualTo(7L);
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void rotate_withinGraceAfterLogout_throws() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(token(NOW.minusSeconds(2), RevokeReason.ROTATED)));
        given(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(FAMILY)).willReturn(false);

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_loggedOutToken_throwsWithoutReuseAlarm() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(token(NOW.minusSeconds(60), RevokeReason.LOGOUT)));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void rotate_expiredToken_throws() {
        RefreshToken expired = token(null, null);
        expired.setExpiresAt(NOW);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_unknownToken_throws() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revoke_knownToken_revokesFamilyAsLogout() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token(null, null)));

        service.revoke("raw");

        verify(refreshTokenRepository).revokeFamily(FAMILY, RevokeReason.LOGOUT, NOW);
    }

    private RefreshToken token(Instant revokedAt, RevokeReason reason) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(FAMILY);
        token.setTokenHash(RefreshTokenService.hash("raw"));
        token.setExpiresAt(NOW.plus(Duration.ofDays(1)));
        token.setRevokedAt(revokedAt);
        token.setRevokeReason(reason);
        return token;
    }
}
