package com.example.hotelbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;
import com.example.hotelbooking.booking.domain.Role;
import com.example.hotelbooking.booking.domain.UserAccount;
import com.example.hotelbooking.booking.dto.BookingCreateRequest;
import com.example.hotelbooking.booking.dto.RoomView;
import com.example.hotelbooking.booking.exception.BookingExceptions.HotelUnavailableException;

class BookingOrchestratorTest {
    private BookingTransactionService transactions;
    private HotelClient hotelClient;
    private BookingOrchestrator orchestrator;
    private UserAccount user;
    private Booking pending;

    @BeforeEach
    void setUp() {
        transactions = mock(BookingTransactionService.class);
        hotelClient = mock(HotelClient.class);
        orchestrator = new BookingOrchestrator(transactions, hotelClient, new RoomPlanner());
        user = mock(UserAccount.class);
        when(user.getId()).thenReturn(42L);
        when(user.getUsername()).thenReturn("demo");
        when(user.getRole()).thenReturn(Role.USER);
        pending = mock(Booking.class);
        when(pending.getId()).thenReturn(100L);
        when(pending.getRequestId()).thenReturn("req-100");
        when(pending.getStatus()).thenReturn(BookingStatus.PENDING);
        when(pending.getRoomId()).thenReturn(null);
        when(transactions.findUserByUsername("demo")).thenReturn(Optional.of(user));
        when(transactions.findByRequestId("req-100")).thenReturn(Optional.empty());
        when(transactions.createPending(eq("req-100"), eq(42L), any(), any(), any())).thenReturn(pending);
    }

    @Test
    void autoSelectUsesLeastBookedAvailableRoomAndConfirmsBooking() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);
        BookingCreateRequest request = new BookingCreateRequest("req-100", 999L, start, end, true);
        Booking confirmed = confirmedBooking(100L, "req-100", 42L, 2L, start, end);
        when(hotelClient.recommend(start, end)).thenReturn(List.of(
                new RoomView(8L, 1L, "A", "108", true, 2),
                new RoomView(2L, 1L, "A", "102", true, 0),
                new RoomView(1L, 1L, "A", "101", false, 0)));
        when(transactions.markConfirmed(100L)).thenReturn(confirmed);

        var response = orchestrator.create(request, "demo");

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.roomId()).isEqualTo(2L);
        verify(hotelClient).reserve(2L, "req-100", start, end);
        verify(hotelClient).commit(2L, "req-100");
        verify(hotelClient, never()).reserve(eq(8L), anyString(), any(), any());
    }

    @Test
    void downstreamFailureCancelsBookingAndAttemptsCompensation() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);
        BookingCreateRequest request = new BookingCreateRequest("req-100", 9L, start, end, false);
        doThrow(new HotelUnavailableException("timeout", new RuntimeException("read timed out")))
                .when(hotelClient).reserve(9L, "req-100", start, end);
        when(transactions.markCancelled(100L)).thenReturn(pending);

        assertThatThrownBy(() -> orchestrator.create(request, "demo"))
                .isInstanceOf(HotelUnavailableException.class);

        verify(transactions).markCancelled(100L);
        verify(hotelClient).release(9L, "req-100");
        verify(transactions, never()).markConfirmed(100L);
    }

    @Test
    void autoSelectSkipsConflictingCandidateAndUsesNextRoom() {
        LocalDate start = LocalDate.now().plusDays(12);
        LocalDate end = start.plusDays(3);
        BookingCreateRequest request = new BookingCreateRequest("req-100", null, start, end, true);
        when(hotelClient.recommend(start, end)).thenReturn(List.of(
                new RoomView(2L, 1L, "A", "102", true, 0),
                new RoomView(8L, 1L, "A", "108", true, 2)));
        doThrow(new com.example.hotelbooking.booking.exception.BookingExceptions.RoomUnavailableException("overlap"))
                .when(hotelClient).reserve(2L, "req-100", start, end);
        Booking confirmed = confirmedBooking(100L, "req-100", 42L, 8L, start, end);
        when(transactions.markConfirmed(100L)).thenReturn(confirmed);

        var response = orchestrator.create(request, "demo");

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.roomId()).isEqualTo(8L);
        verify(hotelClient).reserve(2L, "req-100", start, end);
        verify(hotelClient).reserve(8L, "req-100", start, end);
        verify(hotelClient).commit(8L, "req-100");
    }

    @Test
    void duplicateConfirmedRequestReturnsExistingBookingWithoutRepeatingRemoteCalls() {
        LocalDate start = LocalDate.now().plusDays(14);
        LocalDate end = start.plusDays(2);
        BookingCreateRequest request = new BookingCreateRequest("req-100", 2L, start, end, false);
        Booking existing = confirmedBooking(100L, "req-100", 42L, 2L, start, end);
        when(transactions.findByRequestId("req-100")).thenReturn(Optional.of(existing));

        var response = orchestrator.create(request, "demo");

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.roomId()).isEqualTo(2L);
        verify(transactions, never()).createPending(anyString(), anyLong(), any(), any(), any());
        verify(hotelClient, never()).reserve(anyLong(), anyString(), any(), any());
        verify(hotelClient, never()).commit(anyLong(), anyString());
    }

    @Test
    void commitFailureCancelsBookingAndReleasesReservedRoom() {
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(2);
        BookingCreateRequest request = new BookingCreateRequest("req-100", 9L, start, end, false);

        doThrow(new HotelUnavailableException("commit failed", new RuntimeException("connection reset")))
                .when(hotelClient).commit(9L, "req-100");
        when(transactions.markCancelled(100L)).thenReturn(pending);

        assertThatThrownBy(() -> orchestrator.create(request, "demo"))
                .isInstanceOf(HotelUnavailableException.class);

        verify(hotelClient).reserve(9L, "req-100", start, end);
        verify(hotelClient).commit(9L, "req-100");
        verify(transactions).markCancelled(100L);
        verify(hotelClient).release(9L, "req-100");
        verify(transactions, never()).markConfirmed(100L);
    }
    private Booking confirmedBooking(Long id, String requestId, Long userId, Long roomId,
                                      LocalDate start, LocalDate end) {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(id);
        when(booking.getRequestId()).thenReturn(requestId);
        when(booking.getUserId()).thenReturn(userId);
        when(booking.getRoomId()).thenReturn(roomId);
        when(booking.getStartDate()).thenReturn(start);
        when(booking.getEndDate()).thenReturn(end);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getCreatedAt()).thenReturn(java.time.Instant.now());
        return booking;
    }
}
