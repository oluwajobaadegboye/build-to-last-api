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

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel WHERE p.btlCode = :btlCode")
    Optional<Participant> findByBtlCodeWithHotel(String btlCode);

    Optional<Participant> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Participant> findByEmailIgnoreCaseAndProgramId(String email, String programId);

    long countByNeedsAttentionTrue();

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel ORDER BY p.createdAt DESC")
    List<Participant> findAllWithHotel();

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel WHERE p.programId = :programId ORDER BY p.createdAt DESC")
    List<Participant> findAllByProgramIdWithHotel(String programId);

    long countByProgramId(String programId);

    long countByProgramIdAndNeedsAttentionTrue(String programId);

    List<Participant> findAllByProgramId(String programId);

    long countByBreakoutGroupAndProgramId(Integer breakoutGroup, String programId);
}
