package com.smartlife.service;

import com.smartlife.common.JwtUtil;
import com.smartlife.config.JwtConfig;
import com.smartlife.model.RefreshToken;
import com.smartlife.model.User;
import com.smartlife.repository.RefreshTokenRepository;
import com.smartlife.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;

    @Transactional
    public Map<String, Object> register(String name, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .build();
        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    @Transactional
    public Map<String, Object> refresh(String rawRefreshToken) {
        RefreshToken rt = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt);
            throw new IllegalArgumentException("Refresh token expired");
        }
        User user = rt.getUser();
        String newToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        return Map.of("token", newToken);
    }

    private Map<String, Object> buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        String rawRefresh = UUID.randomUUID().toString();

        // remove old refresh tokens for this user
        refreshTokenRepository.deleteByUser(user);

        RefreshToken rt = RefreshToken.builder()
                .token(rawRefresh)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtConfig.getRefreshExpiration()))
                .build();
        refreshTokenRepository.save(rt);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("refreshToken", rawRefresh);
        result.put("user", userMap);
        return result;
    }
}
