package com.example.hotelbooking.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class CorrelationIdFilterTest {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesValidCorrelationId() {
        assertThat(invoke("test-123_abc")).isEqualTo("test-123_abc");
    }

    @Test
    void replacesTooLongCorrelationId() {
        assertThat(invoke("a".repeat(101))).matches(UUID_PATTERN.pattern());
    }

    @Test
    void replacesCorrelationIdWithInvalidCharacters() {
        assertThat(invoke("bad id")).matches(UUID_PATTERN.pattern());
    }

    @Test
    void generatesIdWhenHeaderIsMissing() {
        assertThat(invoke(null)).matches(UUID_PATTERN.pattern());
    }

    private String invoke(String incomingId) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            forwarded.set(invocation.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/test");
        if (incomingId != null) {
            builder.header(CorrelationIdFilter.HEADER, incomingId);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());

        filter.filter(exchange, chain).block();

        String responseId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER);
        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER)).isEqualTo(responseId);
        return responseId;
    }
}