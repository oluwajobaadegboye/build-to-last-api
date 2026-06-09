package com.btl.transport.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface RunParticipantRepository extends JpaRepository<RunParticipant, RunParticipantId> {

    List<RunParticipant> findByIdRunId(Integer runId);

    List<RunParticipant> findByIdParticipantId(Integer participantId);

    @Query("SELECT rp FROM RunParticipant rp WHERE rp.id.participantId = :participantId AND rp.id.runId IN :runIds")
    List<RunParticipant> findByParticipantIdAndRunIds(
        @Param("participantId") Integer participantId,
        @Param("runIds") List<Integer> runIds
    );

    @Modifying
    @Transactional
    @Query("UPDATE RunParticipant rp SET rp.boarded = :boarded, rp.boardedAt = :boardedAt " +
           "WHERE rp.id.runId = :runId AND rp.id.participantId = :participantId")
    void setBoarded(@Param("runId") Integer runId,
                    @Param("participantId") Integer participantId,
                    @Param("boarded") boolean boarded,
                    @Param("boardedAt") OffsetDateTime boardedAt);

    @Query("SELECT COUNT(rp) FROM RunParticipant rp WHERE rp.id.runId = :runId AND rp.boarded = true")
    long countBoardedByRunId(@Param("runId") Integer runId);

    @Query("SELECT rp.id.participantId FROM RunParticipant rp JOIN Run r ON rp.id.runId = r.id " +
           "WHERE rp.id.participantId IN :participantIds AND rp.boarded = true " +
           "AND r.runType = com.btl.transport.common.enums.RunType.AIRPORT " +
           "AND r.direction = com.btl.transport.common.enums.Direction.TO_HOTEL " +
           "AND r.programId = :programId")
    List<Integer> findBoardedArrivalParticipantIds(
        @Param("participantIds") List<Integer> participantIds,
        @Param("programId") String programId
    );

    @Query("SELECT rp.id.participantId FROM RunParticipant rp JOIN Run r ON rp.id.runId = r.id " +
           "WHERE rp.id.participantId IN :participantIds AND rp.boarded = true " +
           "AND r.runType = com.btl.transport.common.enums.RunType.AIRPORT " +
           "AND r.direction = com.btl.transport.common.enums.Direction.TO_AIRPORT " +
           "AND r.programId = :programId")
    List<Integer> findBoardedDepartureParticipantIds(
        @Param("participantIds") List<Integer> participantIds,
        @Param("programId") String programId
    );
}
