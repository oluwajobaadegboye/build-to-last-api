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
import java.util.Optional;

@Repository
public interface RunRepository extends JpaRepository<Run, Integer> {

    List<Run> findByConferenceDateOrderByDepartTimeAsc(LocalDate date);

    List<Run> findByConferenceDateAndDirectionOrderByDepartTimeAsc(LocalDate date, Direction direction);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver WHERE r.conferenceDate = :date ORDER BY r.departTime ASC")
    List<Run> findByConferenceDateWithDetailsOrderByDepartTimeAsc(@Param("date") LocalDate date);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver WHERE r.conferenceDate = :date AND r.direction = :direction ORDER BY r.departTime ASC")
    List<Run> findByConferenceDateAndDirectionWithDetailsOrderByDepartTimeAsc(@Param("date") LocalDate date, @Param("direction") Direction direction);

    List<Run> findByConferenceDayAndDirection(ConferenceDay day, Direction direction);

    boolean existsByConferenceDateAndDepartTimeAndDirection(LocalDate date, String departTime, Direction direction);

    @Query("""
        SELECT r FROM Run r
        WHERE r.status = com.btl.transport.common.enums.RunStatusEnum.SCHEDULED
          AND r.manifestSent = false
          AND r.conferenceDate = CURRENT_DATE
        """)
    List<Run> findScheduledRunsForToday();

    long countByConferenceDate(LocalDate date);

    Optional<Run> findByRunId(String runId);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver WHERE r.id IN :ids")
    List<Run> findAllByIdWithDetails(@Param("ids") List<Integer> ids);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver")
    List<Run> findAllWithDetails();

    List<Run> findByDriverIdOrderByConferenceDateAscDepartTimeAsc(Integer driverId);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver WHERE r.programId = :programId AND r.conferenceDate = :date ORDER BY r.departTime ASC")
    List<Run> findByProgramIdAndConferenceDateWithDetailsOrderByDepartTimeAsc(@Param("programId") String programId, @Param("date") LocalDate date);

    @Query("SELECT r FROM Run r LEFT JOIN FETCH r.vehicle LEFT JOIN FETCH r.driver WHERE r.programId = :programId AND r.conferenceDate = :date AND r.direction = :direction ORDER BY r.departTime ASC")
    List<Run> findByProgramIdAndConferenceDateAndDirectionWithDetails(@Param("programId") String programId, @Param("date") LocalDate date, @Param("direction") Direction direction);

    long countByProgramIdAndConferenceDate(String programId, LocalDate date);

    @Query("""
        SELECT r FROM Run r
        WHERE r.status = :status
          AND r.manifestSent = false
          AND r.conferenceDate = CURRENT_DATE
        """)
    List<Run> findByStatusAndManifestNotSent(@Param("status") RunStatusEnum status);
}
