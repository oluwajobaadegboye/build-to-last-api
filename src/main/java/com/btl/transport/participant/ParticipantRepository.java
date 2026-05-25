package com.btl.transport.participant;

import com.btl.transport.common.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    Optional<Participant> findByBtlCode(String btlCode);

    Optional<Participant> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    long countByNeedsAttentionTrue();

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel ORDER BY p.createdAt DESC")
    List<Participant> findAllWithHotel();
}
