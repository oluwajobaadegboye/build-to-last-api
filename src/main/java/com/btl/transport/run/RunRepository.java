package com.btl.transport.run;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface RunRepository extends JpaRepository<Run, Integer> {

    List<Run> findByConferenceDateOrderByDepartTimeAsc(LocalDate date);

    List<Run> findByConferenceDateAndDirectionOrderByDepartTimeAsc(LocalDate date, Direction direction);

    List<Run> findByConferenceDayAndDirection(ConferenceDay day, Direction direction);

    boolean existsByConferenceDateAndDepartTimeAndDirection(LocalDate date, String departTime, Direction direction);

    @Query("""
        SELECT r FROM Run r
        WHERE r.status = 'SCHEDULED'
          AND r.manifestSent = false
          AND r.conferenceDate = CURRENT_DATE
        """)
    List<Run> findScheduledRunsForToday();

    long countByConferenceDate(LocalDate date);

    @Query("""
        SELECT r FROM Run r
        WHERE r.status = :status
          AND r.manifestSent = false
          AND r.conferenceDate = CURRENT_DATE
        """)
    List<Run> findByStatusAndManifestNotSent(@Param("status") RunStatusEnum status);
}
