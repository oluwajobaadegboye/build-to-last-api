package com.btl.transport.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramAdminRepository extends JpaRepository<ProgramAdmin, Integer> {
    List<ProgramAdmin> findByAdminUserId(Integer adminUserId);
    List<ProgramAdmin> findByProgramIdOrderByCreatedAtAsc(String programId);
    Optional<ProgramAdmin> findByAdminUserIdAndProgramId(Integer adminUserId, String programId);
    boolean existsByAdminUserIdAndProgramId(Integer adminUserId, String programId);
    long countByProgramId(String programId);
    long countByAdminUserId(Integer adminUserId);
}
