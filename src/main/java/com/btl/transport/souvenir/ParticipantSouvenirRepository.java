package com.btl.transport.souvenir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantSouvenirRepository extends JpaRepository<ParticipantSouvenir, Integer> {
    List<ParticipantSouvenir> findByProgramId(String programId);
    List<ParticipantSouvenir> findByParticipantId(Integer participantId);
    Optional<ParticipantSouvenir> findByParticipantIdAndItemId(Integer participantId, Integer itemId);
}
