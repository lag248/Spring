package com.example.hotelbooking.booking.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.hotelbooking.booking.domain.Role;
import com.example.hotelbooking.booking.domain.UserAccount;
import com.example.hotelbooking.booking.dto.AuthRequest;
import com.example.hotelbooking.booking.dto.AuthResponse;
import com.example.hotelbooking.booking.dto.RegisterRequest;
import com.example.hotelbooking.booking.dto.UserAdminRequest;
import com.example.hotelbooking.booking.dto.UserPatchRequest;
import com.example.hotelbooking.booking.dto.UserResponse;
import com.example.hotelbooking.booking.repository.UserRepository;

@Service
public class AuthService {
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered");
        }
        UserAccount user = users.saveAndFlush(new UserAccount(username,
                passwordEncoder.encode(request.password()), Role.USER));
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        UserAccount user = users.findByUsername(request.username().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() { return users.findAll().stream().map(UserResponse::from).toList(); }

    @Transactional
    public UserResponse createByAdmin(UserAdminRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required when creating a user");
        }
        if (users.existsByUsername(request.username())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        UserAccount saved = users.save(new UserAccount(request.username().trim(), passwordEncoder.encode(request.password()), request.role()));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateByAdmin(Long id, UserPatchRequest request) {
        if ((request.username() == null || request.username().isBlank())
                && (request.password() == null || request.password().isBlank())
                && request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one user field must be provided");
        }
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        if (request.username() != null && !request.username().isBlank()) {
            String nextUsername = request.username().trim();
            if (!nextUsername.equals(user.getUsername()) && users.existsByUsername(nextUsername)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            user.setUsername(nextUsername);
        }
        if (request.password() != null && !request.password().isBlank()) user.setPassword(passwordEncoder.encode(request.password()));
        if (request.role() != null) user.setRole(request.role());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public void deleteByAdmin(Long id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        users.delete(user);
    }

    private AuthResponse tokenResponse(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hotel-booking-platform")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_LIFETIME_SECONDS))
                .claim("userId", user.getId())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse("Bearer", token, TOKEN_LIFETIME_SECONDS, user.getId(), user.getUsername(), user.getRole().name());
    }
}
