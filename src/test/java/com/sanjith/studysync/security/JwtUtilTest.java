package com.sanjith.studysync.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing";

    @Test
    void generatesTokenContainingSubjectEmail() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken("alice@test.com");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@test.com");
    }

    @Test
    void validTokenIsValid() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken("alice@test.com");

        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void expiredTokenIsNotValid() throws InterruptedException {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 1);

        String token = jwtUtil.generateToken("alice@test.com");
        Thread.sleep(10);

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsNotValid() {
        JwtUtil issuer = new JwtUtil(SECRET, 60_000);
        JwtUtil verifier = new JwtUtil("a-completely-different-secret-key-of-sufficient-length", 60_000);

        String token = issuer.generateToken("alice@test.com");

        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void malformedTokenIsNotValid() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        assertThat(jwtUtil.isValid("not-a-jwt-token")).isFalse();
    }

    @Test
    void tamperedTokenIsNotValid() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken("alice@test.com");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }
}
