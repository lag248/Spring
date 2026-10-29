package com.example.hotelbooking.booking.service;

import java.util.Comparator;
import java.util.List;
import com.example.hotelbooking.booking.dto.RoomView;
import org.springframework.stereotype.Component;

/** Deterministic low-booking-count-first planner; it intentionally uses no statistical prediction. */
@Component
public class RoomPlanner {
    public List<RoomView> rank(List<RoomView> rooms) {
        if (rooms == null || rooms.isEmpty()) return List.of();
        return rooms.stream()
                .filter(RoomView::available)
                .sorted(Comparator.comparingLong(RoomView::timesBooked).thenComparing(RoomView::id))
                .toList();
    }
}
