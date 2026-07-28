package com.sanjith.studysync.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP token bucket limiting on auth endpoints, to blunt brute-force / credential-stuffing.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/auth/login", "/auth/register");
    private static final int CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (LIMITED_PATHS.contains(request.getRequestURI())) {
            String key = request.getRemoteAddr() + ":" + request.getRequestURI();
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.getWriter().write("Too many requests, please try again later.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_PERIOD).build())
                .build();
    }
}
