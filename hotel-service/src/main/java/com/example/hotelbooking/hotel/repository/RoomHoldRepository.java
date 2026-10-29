package com.example.hotelbooking.hotel.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.hotelbooking.hotel.domain.HoldStatus;
import com.example.hotelbooking.hotel.domain.RoomHold;

public interface RoomHoldRepository extends JpaRepository<RoomHold, Long> {
    Optional<RoomHold> findByRequestId(String requestId);
    boolean existsByRoom_IdAndStatusIn(Long roomId, Collection<HoldStatus> statuses);
    boolean existsByRoom_Id(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from RoomHold h where h.id = :id")
    Optional<RoomHold> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId, Collection<HoldStatus> statuses, LocalDate requestedEnd, LocalDate requestedStart);

    long countByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId, Collection<HoldStatus> statuses, LocalDate requestedEnd, LocalDate requestedStart);
}
