package com.btl.transport.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, Integer> {

    @Query("SELECT ra FROM RoomAssignment ra LEFT JOIN FETCH ra.occupants o LEFT JOIN FETCH o.participant WHERE ra.programId = :programId ORDER BY ra.hotelName, ra.roomLabel")
    List<RoomAssignment> findByProgramIdWithOccupants(@Param("programId") String programId);

    /**
     * Composite key lookup for smart-merge CSV import.
     * Uses explicit IS NULL check so null gender matches NULL in DB (Spring Data's derived
     * method generates AND gender = NULL which never matches).
     */
    @Query("""
        SELECT ra FROM RoomAssignment ra
        WHERE ra.programId = :programId
          AND LOWER(ra.hotelName) = LOWER(:hotelName)
          AND LOWER(ra.roomLabel) = LOWER(:roomLabel)
          AND ((:gender IS NULL AND ra.gender IS NULL)
               OR (:gender IS NOT NULL AND LOWER(ra.gender) = LOWER(:gender)))
        """)
    Optional<RoomAssignment> findByCompositeKey(
        @Param("programId") String programId,
        @Param("hotelName") String hotelName,
        @Param("roomLabel") String roomLabel,
        @Param("gender") String gender
    );

    List<RoomAssignment> findByProgramId(String programId);

    @Query("SELECT ra FROM RoomAssignment ra LEFT JOIN FETCH ra.occupants o LEFT JOIN FETCH o.participant WHERE ra.id = :id")
    Optional<RoomAssignment> findByIdWithOccupants(@Param("id") Integer id);
}
