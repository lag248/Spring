package com.example.hotelbooking.hotel.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.hotelbooking.hotel.domain.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByAvailableTrueOrderByTimesBookedAscIdAsc();
    List<Room> findByHotel_IdOrderByNumberAsc(Long hotelId);
    boolean existsByHotel_IdAndNumber(Long hotelId, String number);
    boolean existsByHotel_Id(Long hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
