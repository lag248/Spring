package com.example.hotelbooking.hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms", indexes = {
        @Index(name = "idx_room_hotel", columnList = "hotel_id"),
        @Index(name = "idx_room_times_booked", columnList = "times_booked")
}, uniqueConstraints = @UniqueConstraint(name = "uk_room_hotel_number", columnNames = {"hotel_id", "number"}))
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @Column(nullable = false, length = 30)
    private String number;
    @Column(nullable = false)
    private boolean available = true;
    @Column(name = "times_booked", nullable = false)
    private long timesBooked = 0;
    @Version
    private Long version;

    protected Room() { }
    public Room(Hotel hotel, String number, boolean available) {
        this.hotel = hotel; this.number = number; this.available = available;
    }
    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public long getTimesBooked() { return timesBooked; }
    public void setTimesBooked(long timesBooked) { this.timesBooked = timesBooked; }
    public void incrementTimesBooked() { this.timesBooked++; }
    public void decrementTimesBooked() { if (timesBooked > 0) this.timesBooked--; }
}
