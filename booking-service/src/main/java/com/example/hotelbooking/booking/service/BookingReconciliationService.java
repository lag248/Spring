package com.example.hotelbooking.booking.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.hotelbooking.booking.domain.Booking;

/**
 * Completes compensation for bookings left in PENDING after a process restart.
 * A release is attempted before the local cancellation so a temporary Hotel
 * Service outage leaves the record retryable on the next scheduled run.
 */
@Service
public class BookingReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(BookingReconciliationService.class);

    private final BookingTransactionService transactions;
    private final HotelClient hotelClient;
    private final long staleAfterMinutes;

    public BookingReconciliationService(BookingTransactionService transactions, HotelClient hotelClient,
                                        @Value("${booking.reconciliation.stale-after-minutes:10}") long staleAfterMinutes) {
        this.transactions = transactions;
        this.hotelClient = hotelClient;
        this.staleAfterMinutes = Math.max(1, staleAfterMinutes);
    }

    @Scheduled(fixedDelayString = "${booking.reconciliation.fixed-delay-ms:30000}",
               initialDelayString = "${booking.reconciliation.initial-delay-ms:30000}")
    public void reconcileStalePendingBookings() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes));
        for (Booking booking : transactions.stalePendingBookings(cutoff)) {
            try {
                if (booking.getRoomId() != null) {
                    hotelClient.release(booking.getRoomId(), booking.getRequestId());
                }
                transactions.markCancelled(booking.getId());
                log.warn("booking_reconciled bookingId={} requestId={} from=PENDING to=CANCELLED traceId={}",
                        booking.getId(), booking.getRequestId(), bookingTraceId());
            } catch (RuntimeException failure) {
                log.error("booking_reconciliation_deferred bookingId={} requestId={} traceId={}",
                        booking.getId(), booking.getRequestId(), bookingTraceId(), failure);
            }
        }
    }

    private String bookingTraceId() {
        return org.slf4j.MDC.get("traceId");
    }
}
