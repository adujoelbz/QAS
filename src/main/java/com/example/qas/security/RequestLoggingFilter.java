package com.example.qas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("http_request method={} path={} status={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), System.currentTimeMillis() - started);
        }
    }
}
