package com.smartlife.auth;

import com.smartlife.auth.dto.LoginRequest;
import com.smartlife.auth.dto.RegisterRequest;
import com.smartlife.auth.repository.UserRepository;
import com.smartlife.auth.service.AuthService;
import com.smartlife.common.exception.SmartLifeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;

    @Test
    void registerAndLoginSuccessfully() {
        var reg = new RegisterRequest("test@smartlife.com", "Test User", "password123");
        var response = authService.register(reg);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.email()).isEqualTo("test@smartlife.com");
        assertThat(userRepository.existsByEmail("test@smartlife.com")).isTrue();
    }

    @Test
    void duplicateEmailThrowsConflict() {
        var reg = new RegisterRequest("dup@smartlife.com", "User", "password123");
        authService.register(reg);

        assertThatThrownBy(() -> authService.register(reg))
                .isInstanceOf(SmartLifeException.class)
                .satisfies(e -> assertThat(((SmartLifeException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        authService.register(new RegisterRequest("user@smartlife.com", "User", "correct123"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@smartlife.com", "wrong")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void refreshTokenGrantsNewAccessToken() {
        var reg = authService.register(new RegisterRequest("refresh@smartlife.com", "User", "pass1234"));
        var refreshed = authService.refresh(new com.smartlife.auth.dto.RefreshTokenRequest(reg.refreshToken()));

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.accessToken()).isNotEqualTo(reg.accessToken());
    }
}
