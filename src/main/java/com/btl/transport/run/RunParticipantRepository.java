package com.btl.transport.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
