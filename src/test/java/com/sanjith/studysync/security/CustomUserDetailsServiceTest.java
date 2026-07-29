package com.sanjith.studysync.security;

import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @Test
    void loadsUserDetailsWhenEmailExists() {
        userDetailsService = new CustomUserDetailsService(userRepository);
        User user = User.builder()
                .id(1L)
                .email("alice@test.com")
                .passwordHash("hashed")
                .name("Alice")
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("alice@test.com");

        assertThat(result.getUsername()).isEqualTo("alice@test.com");
        assertThat(result.getPassword()).isEqualTo("hashed");
    }

    @Test
    void throwsWhenEmailDoesNotExist() {
        userDetailsService = new CustomUserDetailsService(userRepository);
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
