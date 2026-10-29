package com.example.hotelbooking.booking.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incomingId = request.getHeader(HEADER);
        String id = incomingId != null && VALID_ID.matcher(incomingId).matches()
                ? incomingId : UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        MDC.put("traceId", id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}