package com.example.hotelbooking.hotel.domain;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "room_holds", uniqueConstraints = @UniqueConstraint(name = "uk_room_holds_request_id", columnNames = "request_id"),
        indexes = { @Index(name = "idx_hold_room_dates_status", columnList = "room_id,start_date,end_date,status") })
public class RoomHold {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private HoldStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    protected RoomHold() { }
    public RoomHold(String requestId, Room room, LocalDate startDate, LocalDate endDate) {
        this.requestId = requestId; this.room = room; this.startDate = startDate; this.endDate = endDate;
        this.status = HoldStatus.HELD; this.createdAt = Instant.now();
    }
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Room getRoom() { return room; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public HoldStatus getStatus() { return status; }
    public void setStatus(HoldStatus status) { this.status = status; }
}
