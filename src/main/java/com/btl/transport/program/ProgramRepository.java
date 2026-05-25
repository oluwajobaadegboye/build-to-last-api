package com.btl.transport.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, String> {
    List<Program> findAllByOrderByCreatedAtDesc();
}
