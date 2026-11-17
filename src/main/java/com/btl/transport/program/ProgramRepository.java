package com.btl.transport.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, String> {
    /** Programs an account created since a time (anti-spam cap on self-serve creation). */
    /** Programs backed up to a Google Sheet. */
    java.util.List<Program> findBySheetIdIsNotNull();

    long countByCreatedByAdminIdAndCreatedAtAfter(Integer createdByAdminId, java.time.OffsetDateTime after);


    /** Row lock so concurrent registrations can't overshoot a program's capacity. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Program p where p.id = :id")
    java.util.Optional<Program> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") String id);
    List<Program> findAllByOrderByCreatedAtDesc();
    boolean existsByIni(String ini);
    java.util.Optional<Program> findFirstByIniIgnoreCase(String ini);
}
