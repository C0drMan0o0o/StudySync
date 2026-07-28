package com.sanjith.studysync.auth;

import com.sanjith.studysync.auth.dto.AuthResponse;
import com.sanjith.studysync.auth.dto.LoginRequest;
import com.sanjith.studysync.auth.dto.RegisterRequest;
import com.sanjith.studysync.common.exception.EmailAlreadyInUseException;
import com.sanjith.studysync.common.exception.InvalidCredentialsException;
import com.sanjith.studysync.security.JwtUtil;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        return new AuthResponse(jwtUtil.generateToken(user.getEmail()));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthResponse(jwtUtil.generateToken(user.getEmail()));
    }
}
