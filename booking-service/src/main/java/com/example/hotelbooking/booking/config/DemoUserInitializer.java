package com.example.hotelbooking.booking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.hotelbooking.booking.domain.Role;
import com.example.hotelbooking.booking.domain.UserAccount;
import com.example.hotelbooking.booking.repository.UserRepository;

@Configuration
@Profile("!prod")
public class DemoUserInitializer {
    @Bean
    CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            if (users.count() > 0) return;
            users.save(new UserAccount("admin", passwordEncoder.encode("Admin123!"), Role.ADMIN));
            users.save(new UserAccount("demo", passwordEncoder.encode("Password123!"), Role.USER));
        };
    }
}
