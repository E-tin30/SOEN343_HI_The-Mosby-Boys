package com.mosbyboys.dishesbecrazy.technical.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DemoRequestFilter extends OncePerRequestFilter {
    private final DemoStateAccess access;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (request.getMethod()) {
            case "OPTIONS" -> true;
            case "GET" -> "/api/auth/me".equals(path);
            case "POST" -> "/login".equals(path) || "/api/demo/reset".equals(path);
            default -> false;
        };
    }
    // Login/profile loading remain available for recovery. Reset needs exclusive, rather than shared, access.

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        DemoStateAccess.RequestAccess requestAccess;
        try {
            requestAccess = access.beginRequest();
        } catch (IllegalStateException unavailable) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "1");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(unavailable.getMessage());
            return;
        }
        try (requestAccess) {
            chain.doFilter(request, response);
        }
    }
    // Hold access through the full synchronous request, including transaction commits and callbacks, even on failure.
}
