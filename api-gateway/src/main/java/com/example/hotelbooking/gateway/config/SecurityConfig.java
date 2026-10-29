package com.example.hotelbooking.gateway.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(@Value("${security.jwt.secret}") String base64Secret) {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter,
            ObjectMapper objectMapper) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.POST, "/api/user/register", "/api/user/auth").permitAll()
                        .pathMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers("/api/user", "/api/user/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/rooms/*/confirm-availability", "/api/rooms/*/commit", "/api/rooms/*/release").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/hotels/**", "/api/rooms/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/hotels/**", "/api/rooms/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/hotels/**", "/api/rooms/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, exception) -> writeError(exchange, objectMapper,
                                401, "Unauthorized", "Authentication is required or the JWT is invalid"))
                        .accessDeniedHandler((exchange, exception) -> writeError(exchange, objectMapper,
                                403, "Forbidden", "Insufficient permissions for this operation")))
                .build();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper,
                                  int status, String error, String message) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
        try {
            byte[] body = objectMapper.writeValueAsBytes(new ApiError(Instant.now(), status, error, message,
                    exchange.getRequest().getURI().getPath(), traceId));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }
}
