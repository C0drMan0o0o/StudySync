package com.sanjith.studysync.auth;

import com.sanjith.studysync.auth.dto.AuthResponse;
import com.sanjith.studysync.auth.dto.LoginRequest;
import com.sanjith.studysync.auth.dto.RegisterRequest;
import com.sanjith.studysync.common.exception.EmailAlreadyInUseException;
import com.sanjith.studysync.common.exception.InvalidCredentialsException;
import com.sanjith.studysync.security.JwtUtil;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @Test
    void registerSavesUserWithHashedPasswordAndReturnsToken() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
        RegisterRequest request = new RegisterRequest("alice@test.com", "password123", "Alice");
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(jwtUtil.generateToken("alice@test.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@test.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getName()).isEqualTo("Alice");
    }

    @Test
    void registerThrowsWhenEmailAlreadyInUse() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
        RegisterRequest request = new RegisterRequest("alice@test.com", "password123", "Alice");
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyInUseException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
        User user = User.builder()
                .id(1L)
                .email("alice@test.com")
                .passwordHash("hashed-password")
                .name("Alice")
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken("alice@test.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("alice@test.com", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void loginThrowsWhenEmailNotFound() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@test.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginThrowsWhenPasswordIsWrong() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
        User user = User.builder()
                .id(1L)
                .email("alice@test.com")
                .passwordHash("hashed-password")
                .name("Alice")
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@test.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
