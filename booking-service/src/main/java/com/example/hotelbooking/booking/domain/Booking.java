package com.example.hotelbooking.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_booking_room_dates", columnList = "room_id,start_date,end_date"),
        @Index(name = "idx_booking_status", columnList = "status")
}, uniqueConstraints = @UniqueConstraint(name = "uk_booking_request_id", columnNames = "request_id"))
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "room_id")
    private Long roomId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private BookingStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    protected Booking() { }
    public Booking(String requestId, Long userId, Long roomId, LocalDate startDate, LocalDate endDate) {
        this.requestId = requestId; this.userId = userId; this.roomId = roomId;
        this.startDate = startDate; this.endDate = endDate; this.status = BookingStatus.PENDING;
        this.createdAt = Instant.now();
    }
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
