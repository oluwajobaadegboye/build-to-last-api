package com.btl.transport.souvenir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SouvenirItemRepository extends JpaRepository<SouvenirItem, Integer> {
    List<SouvenirItem> findByProgramIdOrderBySortOrderAscIdAsc(String programId);
    List<SouvenirItem> findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(String programId);
}
