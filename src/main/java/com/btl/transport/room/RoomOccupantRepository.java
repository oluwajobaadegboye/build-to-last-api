package com.btl.transport.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomOccupantRepository extends JpaRepository<RoomOccupant, Integer> {

    Optional<RoomOccupant> findFirstByParticipantId(Integer participantId);

    @Query(value = """
        SELECT ro.* FROM room_occupants ro
        JOIN room_assignments ra ON ro.room_id = ra.id
        WHERE ra.program_id = :programId AND LOWER(ro.email) = LOWER(:email)
        LIMIT 1
        """, nativeQuery = true)
    Optional<RoomOccupant> findByProgramIdAndEmailIgnoreCase(
        @Param("programId") String programId, @Param("email") String email
    );

    @Query(value = """
        SELECT ro.* FROM room_occupants ro
        JOIN room_assignments ra ON ro.room_id = ra.id
        WHERE ra.program_id = :programId
          AND regexp_replace(ro.phone, '[^0-9]', '', 'g') = :digits
        LIMIT 1
        """, nativeQuery = true)
    Optional<RoomOccupant> findByProgramIdAndPhoneDigits(
        @Param("programId") String programId, @Param("digits") String digits
    );

    Optional<RoomOccupant> findByRoomIdAndSlot(Integer roomId, Short slot);

    @Query(value = """
        SELECT ro.* FROM room_occupants ro
        JOIN room_assignments ra ON ro.room_id = ra.id
        WHERE ra.program_id = :programId AND ro.participant_id IS NULL
        """, nativeQuery = true)
    List<RoomOccupant> findUnlinkedByProgramId(@Param("programId") String programId);
}
