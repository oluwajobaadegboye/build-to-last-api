package com.btl.transport.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AccommodationContactRepository extends JpaRepository<AccommodationContact, Integer> {

    List<AccommodationContact> findByProgramIdOrderBySortOrderAsc(String programId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AccommodationContact ac WHERE ac.programId = :programId")
    void deleteByProgramId(String programId);
}
