package com.btl.transport.run;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunStatusEnum;
import com.btl.transport.common.enums.RunType;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunService {

    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final ObjectMapper objectMapper;

    /** Seats on an auto-generated shuttle run (matches the admin UI default for new shuttle runs). */
    static final int DEFAULT_SHUTTLE_SEATS = 15;

    /**
     * Creates the program's daily shuttle runs for {@code date} from its morning (hotel → venue)
     * and evening (venue → hotel) time templates. A direction that already has shuttle runs on
     * that date — e.g. set by hand in Daily Schedule — is left untouched, so this never duplicates
     * or overrides admin edits.
     *
     * @return the runs created (empty when the date is outside the program or nothing was due)
     */
    @Transactional
    public List<Run> generateProgramSlotsForDate(Program program, LocalDate date) {
        List<Run> created = new ArrayList<>();
        LocalDate start = ProgramDates.parseDate(program.getStartDate());
        LocalDate end = ProgramDates.parseDate(program.getEndDate());
        if (start == null) return created;
        if (end == null || end.isBefore(start)) end = start;
        if (date.isBefore(start) || date.isAfter(end)) return created;

        created.addAll(createFromTemplate(program, date, Direction.TO_CHURCH, program.getMorningRuns()));
        created.addAll(createFromTemplate(program, date, Direction.TO_HOTEL, program.getEveningRuns()));
        if (!created.isEmpty()) {
            log.info("Generated {} shuttle runs for program {} on {}", created.size(), program.getId(), date);
        }
        return created;
    }

    private List<Run> createFromTemplate(Program program, LocalDate date, Direction direction, String timesJson) {
        List<Run> created = new ArrayList<>();
        List<String> times = parseTimes(timesJson);
        if (times.isEmpty()) return created;
        if (runRepository.existsByProgramIdAndConferenceDateAndDirectionAndRunType(
                program.getId(), date, direction, RunType.SHUTTLE)) {
            return created; // the admin already scheduled this day/direction
        }
        for (String raw : times) {
            LocalTime t = ProgramDates.parseTime(raw);
            if (t == null) {
                log.warn("Program {}: skipping unrecognised shuttle time '{}'", program.getId(), raw);
                continue;
            }
            String hhmm = t.format(DateTimeFormatter.ofPattern("HH:mm"));
            if (created.stream().anyMatch(r -> r.getDepartTime().equals(hhmm))) continue; // duplicate in template
            Run run = Run.builder()
                .runId(generateRunId())
                .runType(RunType.SHUTTLE)
                .direction(direction)
                .conferenceDay(ConferenceDay.of(date.getDayOfWeek()))
                .conferenceDate(date)
                .departTime(hhmm)
                .seatsTotal(DEFAULT_SHUTTLE_SEATS)
                .seatsFilled(0)
                .status(RunStatusEnum.SCHEDULED)
                .manifestSent(false)
                .programId(program.getId())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
            created.add(runRepository.save(run));
        }
        return created;
    }

    private List<String> parseTimes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String generateRunId() {
        return "RUN-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public List<Run> getRunsForDate(LocalDate date) {
        return runRepository.findByConferenceDateOrderByDepartTimeAsc(date);
    }

    public List<Run> getRunsForDateAndDirection(LocalDate date, Direction direction) {
        return runRepository.findByConferenceDateAndDirectionOrderByDepartTimeAsc(date, direction);
    }

    @Transactional
    public void markBoarded(Integer runId, Integer participantId, boolean boarded) {
        runParticipantRepository.setBoarded(runId, participantId, boarded, boarded ? OffsetDateTime.now() : null);
    }
}
