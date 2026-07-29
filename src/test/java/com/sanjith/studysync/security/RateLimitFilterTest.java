package com.sanjith.studysync.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void allowsRequestsToUnlimitedPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/rooms");

        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void allowsRequestsWithinCapacity() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void rejectsRequestsBeyondCapacityWith429() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, filterChain);
        }
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void tracksLimitsPerIpIndependently() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.3");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, filterChain);
        }
        filter.doFilter(request, response, filterChain);
        verify(response).setStatus(429);

        // A different IP has its own bucket and is unaffected by 10.0.0.3 being limited.
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(11)).doFilter(request, response);
    }
}
