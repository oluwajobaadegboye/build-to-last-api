package org.programhub.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {
    List<Announcement> findTop100ByProgramIdOrderByCreatedAtDesc(String programId);
    Optional<Announcement> findByProgramIdAndRequestId(String programId, String requestId);
    List<Announcement> findByStatus(String status);
}
