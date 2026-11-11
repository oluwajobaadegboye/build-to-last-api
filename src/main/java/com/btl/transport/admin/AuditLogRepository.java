package com.btl.transport.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByProgramIdOrderByIdDesc(String programId, Pageable page);
    List<AuditLog> findByProgramIdAndIdLessThanOrderByIdDesc(String programId, Long before, Pageable page);
    List<AuditLog> findByProgramIdAndUsernameOrderByIdDesc(String programId, String username, Pageable page);
    List<AuditLog> findByProgramIdAndUsernameAndIdLessThanOrderByIdDesc(String programId, String username, Long before, Pageable page);
    @org.springframework.data.jpa.repository.Query(
        "select distinct a.username from AuditLog a where a.programId = :programId and a.username is not null order by a.username")
    List<String> findUsernames(@org.springframework.data.repository.query.Param("programId") String programId);
    boolean existsByProgramIdAndActionAndCreatedAtAfter(String programId, String action, java.time.OffsetDateTime after);
}
