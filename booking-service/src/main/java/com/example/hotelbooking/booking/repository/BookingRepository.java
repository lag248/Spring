package com.example.hotelbooking.booking.repository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByRequestId(String requestId);
    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Booking> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, BookingStatus status, Pageable pageable);
    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);
}
