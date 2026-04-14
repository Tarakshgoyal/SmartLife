package com.smartlife.auth.service;

import com.smartlife.auth.dto.*;
import com.smartlife.auth.jwt.JwtUtil;
import com.smartlife.auth.model.RefreshToken;
import com.smartlife.auth.model.Role;
import com.smartlife.auth.model.User;
import com.smartlife.auth.repository.RefreshTokenRepository;
import com.smartlife.auth.repository.UserRepository;
import com.smartlife.common.exception.SmartLifeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new SmartLifeException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.email())
                .fullName(request.fullName())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.of(accessToken, refreshToken,
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new SmartLifeException("User not found", HttpStatus.NOT_FOUND));

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.of(accessToken, refreshToken,
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new SmartLifeException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new SmartLifeException("Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateToken(user);
        String newRefreshToken = createRefreshToken(user);

        refreshTokenRepository.delete(refreshToken);

        return AuthResponse.of(newAccessToken, newRefreshToken,
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    @Transactional
    public void logout(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("User logged out: {}", user.getEmail());
    }

    private String createRefreshToken(User user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(token);
        return token.getToken();
    }
}
