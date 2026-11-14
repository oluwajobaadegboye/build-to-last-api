package com.btl.transport.registration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationSectionRepository extends JpaRepository<RegistrationSection, Integer> {
    List<RegistrationSection> findByProgramIdOrderBySortOrderAscIdAsc(String programId);
}
