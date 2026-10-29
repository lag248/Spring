package com.example.hotelbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.hotelbooking.booking.dto.RoomView;

class RoomPlannerTest {
    private final RoomPlanner planner = new RoomPlanner();

    @Test
    void sortsByBookingCountThenRoomIdAndExcludesUnavailableRooms() {
        List<RoomView> source = List.of(
                new RoomView(9L, 1L, "A", "109", true, 4),
                new RoomView(5L, 1L, "A", "105", true, 0),
                new RoomView(3L, 1L, "A", "103", false, 0),
                new RoomView(2L, 1L, "A", "102", true, 0),
                new RoomView(1L, 1L, "A", "101", true, 2));

        assertThat(planner.rank(source).stream().map(RoomView::id).toList())
                .containsExactly(2L, 5L, 1L, 9L);
    }

    @Test
    void emptyCandidateListReturnsEmptyList() {
        assertThat(planner.rank(List.of())).isEmpty();
        assertThat(planner.rank(null)).isEmpty();
    }
}
