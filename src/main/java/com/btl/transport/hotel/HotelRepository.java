package com.btl.transport.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Integer> {
    List<Hotel> findAllByOrderByShuttleStopOrderAsc();
    List<Hotel> findByProgramIdOrderByShuttleStopOrderAsc(String programId);
    Optional<Hotel> findByProgramIdAndExternalRef(String programId, String externalRef);
    Optional<Hotel> findByProgramIdAndHotelNameIgnoreCase(String programId, String hotelName);
}
