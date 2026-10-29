package com.example.hotelbooking.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.hotelbooking.hotel.domain.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> { }
