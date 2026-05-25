package com.btl.transport.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Integer> {
    Optional<AdminUser> findByUsername(String username);
    Optional<AdminUser> findByUsernameAndProgramId(String username, String programId);
    List<AdminUser> findByProgramIdOrderByCreatedAtAsc(String programId);
    long countByProgramId(String programId);
}
