package com.example.hotelbooking.hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "hotels", indexes = @Index(name = "idx_hotels_name", columnList = "name"))
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 300)
    private String address;

    protected Hotel() { }
    public Hotel(String name, String address) { this.name = name; this.address = address; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
