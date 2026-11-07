package com.btl.transport.scheduler;

import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.run.RunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * Generates each program's daily shuttle runs from its morning/evening time templates.
 * Checks hourly; a program's runs for the day are created during the 6 AM hour in that
 * program's own time zone, on days between its start and end dates. Archived programs are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShuttleSchedulerJob {

    static final int GENERATION_HOUR = 6;

    private final ProgramRepository programRepository;
    private final RunService runService;

    @Scheduled(cron = "${btl.shuttle.generation-cron:0 5 * * * *}", zone = "UTC")
    public void generateDailySlots() {
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
        if (programNow.getHour() != GENERATION_HOUR) return;
        runService.generateProgramSlotsForDate(program, programNow.toLocalDate());
    }
}
