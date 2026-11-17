package org.programhub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, String> {
    /** Programs an account created since a time (anti-spam cap on self-serve creation). */
    /** Programs backed up to a Google Sheet. */
    List<Program> findBySheetIdIsNotNull();

    /** Whether another program already backs up to this spreadsheet. */
    boolean existsBySheetIdAndIdNot(String sheetId, String id);

    long countByCreatedByAdminIdAndCreatedAtAfter(Integer createdByAdminId, OffsetDateTime after);


    /** Row lock so concurrent registrations can't overshoot a program's capacity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Program p where p.id = :id")
    Optional<Program> findByIdForUpdate(@Param("id") String id);
    List<Program> findAllByOrderByCreatedAtDesc();
    boolean existsByIni(String ini);
    Optional<Program> findFirstByIniIgnoreCase(String ini);
}
