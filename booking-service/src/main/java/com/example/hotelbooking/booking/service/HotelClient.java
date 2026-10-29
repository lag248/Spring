package com.example.hotelbooking.booking.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.hotelbooking.booking.dto.AvailabilityRequest;
import com.example.hotelbooking.booking.dto.RequestIdDto;
import com.example.hotelbooking.booking.dto.RoomView;
import com.example.hotelbooking.booking.exception.BookingExceptions.HotelUnavailableException;
import com.example.hotelbooking.booking.exception.BookingExceptions.RoomUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@Component
public class HotelClient {
    private static final Logger log = LoggerFactory.getLogger(HotelClient.class);
    private final RestTemplate restTemplate;
    private final String internalToken;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public HotelClient(RestTemplate restTemplate,
                      @Value("${hotel-client.internal-token}") String internalToken,
                      @Value("${hotel-client.retry.max-attempts:3}") int maxAttempts,
                      @Value("${hotel-client.retry.initial-backoff-ms:150}") long initialBackoffMs) {
        this.restTemplate = restTemplate;
        this.internalToken = internalToken;
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(Math.max(1, maxAttempts))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(initialBackoffMs, 2.0))
                .retryOnException(error -> !(error instanceof HttpClientErrorException)
                        && !(error instanceof RoomUnavailableException))
                .build();
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .ignoreExceptions(HttpClientErrorException.class, RoomUnavailableException.class)
                .build();
        this.retry = Retry.of("hotel-service", retryConfig);
        this.circuitBreaker = CircuitBreaker.of("hotel-service", breakerConfig);
        this.retry.getEventPublisher().onRetry(event -> log.warn("hotel_call_retry attempt={} traceId={} error={}",
                event.getNumberOfRetryAttempts(), org.slf4j.MDC.get("traceId"), event.getLastThrowable() == null ? "unknown" : event.getLastThrowable().getClass().getSimpleName()));
        this.circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn("hotel_circuit_state from={} to={}",
                event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
    }

    public List<RoomView> recommend(LocalDate start, LocalDate end) {
        return resilient("room recommendation", () -> {
            ResponseEntity<List<RoomView>> response = restTemplate.exchange(
                    "http://hotel-service/api/rooms/recommend?startDate={start}&endDate={end}",
                    HttpMethod.GET, userAuthorizedEntity(), new ParameterizedTypeReference<List<RoomView>>() { }, start, end);
            return response.getBody() == null ? List.of() : response.getBody();
        });
    }

    public void reserve(Long roomId, String requestId, LocalDate start, LocalDate end) {
        resilient("reserve room", () -> {
            AvailabilityRequest body = new AvailabilityRequest(requestId, start, end);
            try {
                restTemplate.exchange("http://hotel-service/api/rooms/{id}/confirm-availability", HttpMethod.POST,
                        internalEntity(body), Void.class, roomId);
                return Boolean.TRUE;
            } catch (HttpClientErrorException.Conflict conflict) {
                throw new RoomUnavailableException("Room " + roomId + " is not available for the requested dates");
            }
        });
    }

    public void commit(Long roomId, String requestId) {
        resilient("commit room hold", () -> {
            restTemplate.exchange("http://hotel-service/api/rooms/{id}/commit", HttpMethod.POST,
                    internalEntity(new RequestIdDto(requestId)), Void.class, roomId);
            return Boolean.TRUE;
        });
    }

    /** Compensation bypasses an open circuit breaker but still has bounded retries and HTTP timeouts. */
    public void release(Long roomId, String requestId) {
        try {
            retry.executeSupplier(() -> {
                restTemplate.exchange("http://hotel-service/api/rooms/{id}/release", HttpMethod.POST,
                        internalEntity(new RequestIdDto(requestId)), Void.class, roomId);
                return Boolean.TRUE;
            });
        } catch (RuntimeException ex) {
            log.error("hotel_compensation_failed roomId={} requestId={} traceId={}", roomId, requestId,
                    org.slf4j.MDC.get("traceId"), ex);
            throw new HotelUnavailableException("Hotel Service could not release the temporary reservation; retry the same requestId", ex);
        }
    }

    private <T> T resilient(String operation, Supplier<T> action) {
        try {
            return retry.executeSupplier(() -> circuitBreaker.executeSupplier(action));
        } catch (RoomUnavailableException ex) {
            throw ex;
        } catch (HttpClientErrorException ex) {
            throw new HotelUnavailableException("Hotel Service rejected " + operation + " with HTTP " + ex.getStatusCode().value(), ex);
        } catch (CallNotPermittedException ex) {
            throw new HotelUnavailableException("Hotel Service circuit breaker is open", ex);
        } catch (RuntimeException ex) {
            log.error("hotel_call_failed operation={} traceId={}", operation, org.slf4j.MDC.get("traceId"), ex);
            throw new HotelUnavailableException("Hotel Service is temporarily unavailable during " + operation, ex);
        }
    }

    private HttpEntity<Void> userAuthorizedEntity() {
        HttpHeaders headers = new HttpHeaders();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            headers.setBearerAuth(jwt.getToken().getTokenValue());
        }
        String correlation = org.slf4j.MDC.get("traceId");
        if (correlation != null && !correlation.isBlank()) headers.set("X-Correlation-Id", correlation);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> internalEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        String correlation = org.slf4j.MDC.get("traceId");
        if (correlation != null && !correlation.isBlank()) headers.set("X-Correlation-Id", correlation);
        return new HttpEntity<>(body, headers);
    }
}
