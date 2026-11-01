package com.tripwise.service;

import com.tripwise.dto.AuthResponse;
import com.tripwise.dto.LoginRequest;
import com.tripwise.dto.RegisterRequest;
import com.tripwise.entity.User;
import com.tripwise.exception.DuplicateEmailException;
import com.tripwise.repository.UserRepository;
import com.tripwise.exception.InvalidRefreshTokenException;
import com.tripwise.exception.TooManyRequestsException;
import com.tripwise.security.JwtService;
import com.tripwise.security.LoginRateLimiter;
import com.tripwise.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant EXPIRES = Instant.parse("2030-01-08T00:00:00Z");
    private static final RefreshTokenService.IssuedRefreshToken REFRESH =
            new RefreshTokenService.IssuedRefreshToken(7L, "refresh", EXPIRES);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_newEmail_normalizesEmailHashesPasswordAndReturnsToken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(jwtService.generateToken(7L, "alice@example.com")).thenReturn("jwt");
        when(refreshTokenService.issue(7L)).thenReturn(REFRESH);

        AuthResponse response = authService.register(
                new RegisterRequest("  Alice@Example.COM ", "Secret123!", " Alice Smith "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getFullName()).isEqualTo("Alice Smith");
        assertThat(response).isEqualTo(new AuthResponse("jwt", "alice@example.com", "Alice Smith", 7L, "refresh", EXPIRES));
    }

    @Test
    void register_existingEmail_throwsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "Secret123!", "Alice")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrentDuplicate_throwsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "Secret123!", "Alice")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void login_validCredentials_returnsTokenForPrincipal() {
        UserPrincipal principal = new UserPrincipal(7L, "alice@example.com", "Alice", "hashed");
        Authentication authenticated =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(jwtService.generateToken(7L, "alice@example.com")).thenReturn("jwt");
        when(refreshTokenService.issue(7L)).thenReturn(REFRESH);

        AuthResponse response = authService.login(new LoginRequest(" ALICE@example.com", "Secret123!"));

        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertThat(attempt.getValue().getName()).isEqualTo("alice@example.com");
        assertThat(response).isEqualTo(new AuthResponse("jwt", "alice@example.com", "Alice", 7L, "refresh", EXPIRES));
    }

    @Test
    void login_badCredentials_propagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void login_emailRateLimited_throwsWithoutAuthenticating() {
        doThrow(new TooManyRequestsException(30)).when(loginRateLimiter).checkAttempt("alice@example.com");

        assertThatThrownBy(() -> authService.login(new LoginRequest(" Alice@Example.com", "Secret123!")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void refresh_validToken_returnsNewAccessAndRotatedRefreshToken() {
        User user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
        user.setFullName("Alice");
        when(refreshTokenService.rotate("old")).thenReturn(REFRESH);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(7L, "alice@example.com")).thenReturn("jwt");

        assertThat(authService.refresh("old"))
                .isEqualTo(new AuthResponse("jwt", "alice@example.com", "Alice", 7L, "refresh", EXPIRES));
    }

    @Test
    void refresh_invalidToken_propagatesWithoutIssuingAccessToken() {
        when(refreshTokenService.rotate("bad")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void logout_revokesSession() {
        authService.logout("token");

        verify(refreshTokenService).revoke("token");
    }
}
