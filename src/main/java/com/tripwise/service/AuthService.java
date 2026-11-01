package com.tripwise.service;

import com.tripwise.dto.AuthResponse;
import com.tripwise.dto.LoginRequest;
import com.tripwise.dto.MeResponse;
import com.tripwise.dto.RegisterRequest;
import com.tripwise.entity.User;
import com.tripwise.exception.DuplicateEmailException;
import com.tripwise.exception.InvalidRefreshTokenException;
import com.tripwise.exception.TooManyRequestsException;
import com.tripwise.repository.UserRepository;
import com.tripwise.security.CurrentUserProvider;
import com.tripwise.security.JwtService;
import com.tripwise.security.LoginRateLimiter;
import com.tripwise.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUserProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same email hit the unique constraint
            throw new DuplicateEmailException();
        }

        log.info("Registered user id={}", user.getId());
        return toResponse(user.getId(), user.getEmail(), user.getFullName());
    }

    /**
     * Throws {@link org.springframework.security.authentication.BadCredentialsException} for an unknown email
     * or wrong password (same error for both, to avoid user enumeration).
     *
     * @throws TooManyRequestsException when this email has too many recent attempts
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        loginRateLimiter.checkAttempt(email);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return toResponse(principal.id(), principal.email(), principal.fullName());
    }

    /**
     * Exchanges a refresh token for a new access token and a rotated refresh token.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, revoked or reused
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(issued.userId()).orElseThrow(InvalidRefreshTokenException::new);
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail()), user.getEmail(),
                user.getFullName(), user.getId(), issued.token(), issued.expiresAt());
    }

    /**
     * Revokes the session of the given refresh token; access tokens already issued expire on their own.
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * A valid token whose user no longer exists is treated as unauthenticated (401), so clients log out.
     */
    @Transactional(readOnly = true)
    public MeResponse me() {
        Long userId = currentUserProvider.getCurrentUserId();
        return userRepository.findById(userId)
                .map(user -> new MeResponse(user.getId(), user.getEmail(), user.getFullName()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User no longer exists"));
    }

    private AuthResponse toResponse(Long userId, String email, String fullName) {
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(userId);
        return new AuthResponse(jwtService.generateToken(userId, email), email, fullName, userId, refresh.token(),
                refresh.expiresAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
