package com.btl.transport.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelChaperoneRepository extends JpaRepository<HotelChaperone, Integer> {
    List<HotelChaperone> findByProgramIdOrderByHotelNameAscIdAsc(String programId);
}
