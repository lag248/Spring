package com.example.hotelbooking.booking.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.hotelbooking.booking.domain.UserAccount;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
