package com.btl.transport.unit;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunType;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
import com.btl.transport.run.RunService;
import com.btl.transport.scheduler.ShuttleSchedulerJob;
import com.btl.transport.program.ProgramRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramShuttleGenerationTest {

    private RunRepository runRepository;
    private RunService runService;

    @BeforeEach
    void setUp() {
        runRepository = mock(RunRepository.class);
        when(runRepository.save(any(Run.class))).thenAnswer(inv -> inv.getArgument(0));
        runService = new RunService(runRepository, mock(RunParticipantRepository.class), new ObjectMapper());
    }

    private Program program(String start, String end) {
        Program p = new Program();
        p.setId("p1");
        p.setStartDate(start);
        p.setEndDate(end);
        p.setTimezone("America/Denver");
        p.setMorningRuns("[\"7:30 AM\", \"8:00 AM\", \"7:30 AM\"]");
        p.setEveningRuns("[\"8:30 PM\"]");
        return p;
    }

    // ── ProgramDates ──

    @Test
    void parses_display_and_iso_dates() {
        assertThat(ProgramDates.parseDate("Mar 1, 2027")).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ProgramDates.parseDate("March 1, 2027")).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ProgramDates.parseDate("2027-03-01")).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ProgramDates.parseDate("")).isNull();
        assertThat(ProgramDates.parseDate("soon")).isNull();
    }

    @Test
    void parses_12h_and_24h_times() {
        assertThat(ProgramDates.parseTime("7:30 AM")).isEqualTo(LocalTime.of(7, 30));
        assertThat(ProgramDates.parseTime("8:30 pm")).isEqualTo(LocalTime.of(20, 30));
        assertThat(ProgramDates.parseTime("19:15")).isEqualTo(LocalTime.of(19, 15));
        assertThat(ProgramDates.parseTime("noonish")).isNull();
    }

    // ── RunService.generateProgramSlotsForDate ──

    @Test
    void generates_template_runs_for_a_program_day_with_program_id_and_weekday() {
        List<Run> runs = runService.generateProgramSlotsForDate(program("Mar 1, 2027", "Mar 3, 2027"), LocalDate.of(2027, 3, 1));

        // 7:30 AM appears twice in the template but is created once.
        assertThat(runs).extracting(Run::getDepartTime).containsExactly("07:30", "08:00", "20:30");
        assertThat(runs).allSatisfy(r -> {
            assertThat(r.getProgramId()).isEqualTo("p1");
            assertThat(r.getRunType()).isEqualTo(RunType.SHUTTLE);
            assertThat(r.getConferenceDay()).isEqualTo(ConferenceDay.MONDAY);
            assertThat(r.getConferenceDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        });
        assertThat(runs).filteredOn(r -> r.getDirection() == Direction.TO_CHURCH).hasSize(2);
        assertThat(runs).filteredOn(r -> r.getDirection() == Direction.TO_HOTEL).hasSize(1);
    }

    @Test
    void skips_dates_outside_the_program() {
        Program p = program("Mar 1, 2027", "Mar 3, 2027");
        assertThat(runService.generateProgramSlotsForDate(p, LocalDate.of(2027, 2, 28))).isEmpty();
        assertThat(runService.generateProgramSlotsForDate(p, LocalDate.of(2027, 3, 4))).isEmpty();
        verify(runRepository, never()).save(any());
    }

    @Test
    void skips_programs_without_dates() {
        assertThat(runService.generateProgramSlotsForDate(program(null, null), LocalDate.of(2027, 3, 1))).isEmpty();
    }

    @Test
    void leaves_a_direction_alone_when_the_admin_already_scheduled_it() {
        when(runRepository.existsByProgramIdAndConferenceDateAndDirectionAndRunType(
            "p1", LocalDate.of(2027, 3, 2), Direction.TO_CHURCH, RunType.SHUTTLE)).thenReturn(true);

        List<Run> runs = runService.generateProgramSlotsForDate(program("Mar 1, 2027", "Mar 3, 2027"), LocalDate.of(2027, 3, 2));

        assertThat(runs).extracting(Run::getDirection).containsOnly(Direction.TO_HOTEL);
    }

    // ── ShuttleSchedulerJob.generateIfDue ──

    @Test
    void job_generates_only_during_6am_in_the_programs_zone_and_skips_archived() {
        RunService service = mock(RunService.class);
        ShuttleSchedulerJob job = new ShuttleSchedulerJob(mock(ProgramRepository.class), service);
        Program p = program("Mar 1, 2027", "Mar 3, 2027");
        ZoneId denver = ZoneId.of("America/Denver");

        job.generateIfDue(p, ZonedDateTime.of(2027, 3, 1, 5, 59, 0, 0, denver));
        job.generateIfDue(p, ZonedDateTime.of(2027, 3, 1, 7, 0, 0, 0, denver));
        verify(service, never()).generateProgramSlotsForDate(any(), any());

        job.generateIfDue(p, ZonedDateTime.of(2027, 3, 1, 6, 5, 0, 0, denver));
        verify(service).generateProgramSlotsForDate(p, LocalDate.of(2027, 3, 1));

        p.setPhase("archived");
        job.generateIfDue(p, ZonedDateTime.of(2027, 3, 2, 6, 5, 0, 0, denver));
        verify(service, never()).generateProgramSlotsForDate(p, LocalDate.of(2027, 3, 2));
    }
}
