package org.programhub.scheduler;

import org.programhub.program.Program;
import org.programhub.program.ProgramDates;
import org.programhub.program.ProgramRepository;
import org.programhub.run.RunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Generates each program's daily shuttle runs from its morning/evening time templates.
 * Checks hourly; a program's runs for the day are created during the 6 AM hour in that
 * program's own time zone, on days between its start and end dates. Archived programs and programs
 * with the daily shuttle turned off are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShuttleSchedulerJob {

    static final int GENERATION_HOUR = 6;

    private final ProgramRepository programRepository;
    private final RunService runService;

    /** Only one server copy runs each round (absent in unit tests, which build the job directly). */
    @Autowired(required = false)
    private JobLock jobLock;

    @Scheduled(cron = "${btl.shuttle.generation-cron:0 5 * * * *}", zone = "UTC")
    public void generateDailySlots() {
        if (jobLock != null && !jobLock.acquire("shuttle-generation", Duration.ofMinutes(50))) return;
        for (Program program : programRepository.findAllByOrderByCreatedAtDesc()) {
            try {
                generateIfDue(program, ZonedDateTime.now(ProgramDates.zone(program)));
            } catch (Exception e) {
                // One bad program must not stop the others.
                log.warn("Shuttle generation failed for program {}: {}", program.getId(), e.getMessage());
            }
        }
    }

    /** Generates today's runs when it is the generation hour in the program's zone. */
    public void generateIfDue(Program program, ZonedDateTime programNow) {
        if ("archived".equalsIgnoreCase(program.getPhase())) return;
        if (Boolean.FALSE.equals(program.getDailyShuttleEnabled())) return;
        if (programNow.getHour() != GENERATION_HOUR) return;
        runService.generateProgramSlotsForDate(program, programNow.toLocalDate());
    }
}
