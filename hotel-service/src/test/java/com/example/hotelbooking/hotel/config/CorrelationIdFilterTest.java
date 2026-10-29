package com.example.hotelbooking.hotel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesValidCorrelationId() throws Exception {
        assertThat(invoke("test-123_abc")).isEqualTo("test-123_abc");
    }

    @Test
    void replacesTooLongCorrelationId() throws Exception {
        assertThat(invoke("a".repeat(101))).matches(UUID_PATTERN.pattern());
    }

    @Test
    void replacesCorrelationIdWithInvalidCharacters() throws Exception {
        assertThat(invoke("bad id")).matches(UUID_PATTERN.pattern());
    }

    @Test
    void generatesIdWhenHeaderIsMissing() throws Exception {
        assertThat(invoke(null)).matches(UUID_PATTERN.pattern());
    }

    private String invoke(String incomingId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingId != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incomingId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(MDC.get("traceId")).isNull();
        return response.getHeader(CorrelationIdFilter.HEADER);
    }
}