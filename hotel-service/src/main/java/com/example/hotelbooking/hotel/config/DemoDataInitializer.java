package com.example.hotelbooking.hotel.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import com.example.hotelbooking.hotel.repository.RoomRepository;

@Configuration
@Profile("!prod")
public class DemoDataInitializer {
    @Bean
    CommandLineRunner seedDemoData(HotelRepository hotels, RoomRepository rooms) {
        return args -> {
            if (hotels.count() > 0) return;
            Hotel central = hotels.save(new Hotel("Central Park Hotel", "1 Example Street"));
            Hotel river = hotels.save(new Hotel("Riverside Hotel", "24 River Avenue"));
            Room a101 = new Room(central, "101", true); a101.setTimesBooked(0); rooms.save(a101);
            Room a102 = new Room(central, "102", true); a102.setTimesBooked(3); rooms.save(a102);
            Room a103 = new Room(central, "103", true); a103.setTimesBooked(1); rooms.save(a103);
            Room b201 = new Room(river, "201", true); b201.setTimesBooked(2); rooms.save(b201);
            Room b202 = new Room(river, "202", true); b202.setTimesBooked(0); rooms.save(b202);
        };
    }
}
