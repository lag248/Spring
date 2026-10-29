package com.example.hotelbooking.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.hotelbooking.hotel.domain.HoldStatus;
import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.dto.AvailabilityRequest;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import com.example.hotelbooking.hotel.repository.RoomHoldRepository;
import com.example.hotelbooking.hotel.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:hotel-concurrency-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
    }
)
@ActiveProfiles("prod")
class RoomManagementConcurrencyIntegrationTest {

    @Autowired
    private RoomManagementService roomManagementService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomHoldRepository roomHoldRepository;

    @BeforeEach
    void cleanDatabase() {
        roomHoldRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void concurrentOverlappingReservationsForSameRoomAllowOnlyOneHold() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Concurrency Test Hotel", "Test Address"));
        Room room = roomRepository.save(new Room(hotel, "TEST-101", true));

        LocalDate startDate = LocalDate.now().plusDays(30);
        LocalDate endDate = startDate.plusDays(3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<String> first = reservationAttempt(
                room.getId(), "concurrency-request-A", startDate, endDate, ready, start
            );
            Callable<String> second = reservationAttempt(
                room.getId(), "concurrency-request-B", startDate, endDate, ready, start
            );

            Future<String> firstResult = executor.submit(first);
            Future<String> secondResult = executor.submit(second);

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both worker threads should be ready");
            start.countDown();

            List<String> outcomes = List.of(
                firstResult.get(20, TimeUnit.SECONDS),
                secondResult.get(20, TimeUnit.SECONDS)
            );

            assertEquals(1L, outcomes.stream().filter("SUCCESS"::equals).count(),
                "Exactly one reservation should succeed");
            assertEquals(1L, outcomes.stream().filter("CONFLICT"::equals).count(),
                "The overlapping reservation should receive HTTP 409");

            long activeOverlappingHolds =
                roomHoldRepository.countByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
                    room.getId(),
                    List.of(HoldStatus.HELD, HoldStatus.CONFIRMED),
                    endDate,
                    startDate
                );

            assertEquals(1L, activeOverlappingHolds,
                "The database should contain exactly one active overlapping hold");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateDeliveriesAreIdempotent() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Duplicate Delivery Hotel", "Test Address"));
        Room room = roomRepository.save(new Room(hotel, "DUP-101", true));
        LocalDate startDate = LocalDate.now().plusDays(40);
        LocalDate endDate = startDate.plusDays(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> first = duplicateAttempt(room.getId(), startDate, endDate, ready, start);
            Callable<String> second = duplicateAttempt(room.getId(), startDate, endDate, ready, start);
            Future<String> firstResult = executor.submit(first);
            Future<String> secondResult = executor.submit(second);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals("SUCCESS", firstResult.get(20, TimeUnit.SECONDS));
            assertEquals("SUCCESS", secondResult.get(20, TimeUnit.SECONDS));
            assertEquals(1L, roomHoldRepository.count());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<String> reservationAttempt(
        Long roomId,
        String requestId,
        LocalDate startDate,
        LocalDate endDate,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        return () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent start");
            }

            try {
                roomManagementService.reserve(
                    roomId,
                    new AvailabilityRequest(requestId, startDate, endDate)
                );
                return "SUCCESS";
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                    return "CONFLICT";
                }
                throw exception;
            }
        };
    }

    private Callable<String> duplicateAttempt(
            Long roomId, LocalDate startDate, LocalDate endDate,
            CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent start");
            }
            roomManagementService.reserve(roomId,
                    new AvailabilityRequest("same-request-id", startDate, endDate));
            return "SUCCESS";
        };
    }
}
