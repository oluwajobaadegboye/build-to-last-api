package com.btl.transport.registration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationFieldRepository extends JpaRepository<RegistrationField, Integer> {
    List<RegistrationField> findByProgramIdOrderBySortOrderAscIdAsc(String programId);
    List<RegistrationField> findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(String programId);
    boolean existsByProgramIdAndFieldKey(String programId, String fieldKey);
}
