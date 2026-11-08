package com.btl.transport.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByProgramIdOrderByIdDesc(String programId, Pageable page);
    List<AuditLog> findByProgramIdAndIdLessThanOrderByIdDesc(String programId, Long before, Pageable page);
}
