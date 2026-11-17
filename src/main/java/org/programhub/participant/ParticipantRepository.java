package org.programhub.participant;

import org.programhub.common.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    Optional<Participant> findByRegCode(String regCode);

    List<Participant> findByRegCodeIn(Collection<String> regCodes);

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel WHERE p.regCode = :regCode")
    Optional<Participant> findByRegCodeWithHotel(String regCode);

    Optional<Participant> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Participant> findByEmailIgnoreCaseAndProgramId(String email, String programId);

    long countByNeedsAttentionTrue();

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel ORDER BY p.createdAt DESC")
    List<Participant> findAllWithHotel();

    @Query("SELECT p FROM Participant p LEFT JOIN FETCH p.hotel WHERE p.programId = :programId ORDER BY p.createdAt DESC")
    List<Participant> findAllByProgramIdWithHotel(String programId);

    long countByProgramId(String programId);

    /** People with a place (not on the waitlist) — what capacity counts. */
    long countByProgramIdAndWaitlistedAtIsNull(String programId);

    long countByProgramIdAndNeedsAttentionTrue(String programId);

    List<Participant> findAllByProgramId(String programId);

    long countByBreakoutGroupAndProgramId(Integer breakoutGroup, String programId);
}
