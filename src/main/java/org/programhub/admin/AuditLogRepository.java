package org.programhub.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByProgramIdOrderByIdDesc(String programId, Pageable page);
    List<AuditLog> findByProgramIdAndIdLessThanOrderByIdDesc(String programId, Long before, Pageable page);
    List<AuditLog> findByProgramIdAndUsernameOrderByIdDesc(String programId, String username, Pageable page);
    List<AuditLog> findByProgramIdAndUsernameAndIdLessThanOrderByIdDesc(String programId, String username, Long before, Pageable page);
    @Query(
        "select distinct a.username from AuditLog a where a.programId = :programId and a.username is not null order by a.username")
    List<String> findUsernames(@Param("programId") String programId);
    boolean existsByProgramIdAndActionAndCreatedAtAfter(String programId, String action, OffsetDateTime after);
}
