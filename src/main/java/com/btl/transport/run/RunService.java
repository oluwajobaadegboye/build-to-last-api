package com.btl.transport.run;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunStatusEnum;
import com.btl.transport.common.enums.RunType;
import com.btl.transport.notification.ShuttleConfig;
import com.btl.transport.notification.ShuttleConfigRepository;
import jakarta.transaction.Transactional;
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
    private final ShuttleConfigRepository shuttleConfigRepository;

    @Transactional
    public List<Run> generateSlotsForDate(LocalDate date, ConferenceDay conferenceDay) {
        List<ShuttleConfig> configs = shuttleConfigRepository.findByConferenceDay(conferenceDay);
        List<Run> created = new ArrayList<>();

        for (ShuttleConfig config : configs) {
            LocalTime cursor = LocalTime.parse(config.getWindowStart());
            LocalTime end = LocalTime.parse(config.getWindowEnd());

            while (!cursor.isAfter(end)) {
                String timeStr = cursor.format(DateTimeFormatter.ofPattern("HH:mm"));
                Direction dir = config.getDirection();

                if (!runRepository.existsByConferenceDateAndDepartTimeAndDirection(date, timeStr, dir)) {
                    Run run = Run.builder()
                        .runId(generateRunId())
                        .runType(RunType.SHUTTLE)
                        .direction(dir)
                        .conferenceDay(conferenceDay)
                        .conferenceDate(date)
                        .departTime(timeStr)
                        .seatsTotal(config.getSeatsPerVehicle())
                        .seatsFilled(0)
                        .status(RunStatusEnum.SCHEDULED)
                        .manifestSent(false)
                        .createdAt(OffsetDateTime.now())
                        .updatedAt(OffsetDateTime.now())
                        .build();
                    created.add(runRepository.save(run));
                }
                cursor = cursor.plusMinutes(config.getIntervalMins());
            }
        }

        log.info("Generated {} run slots for {}", created.size(), date);
        return created;
    }

    private String generateRunId() {
        long count = runRepository.count() + 1;
        return String.format("RUN-%03d", count);
    }

    public List<Run> getRunsForDate(LocalDate date) {
        return runRepository.findByConferenceDateOrderByDepartTimeAsc(date);
    }

    public List<Run> getRunsForDateAndDirection(LocalDate date, Direction direction) {
        return runRepository.findByConferenceDateAndDirectionOrderByDepartTimeAsc(date, direction);
    }

    @Transactional
    public RunParticipant markBoarded(Integer runId, Integer participantId, boolean boarded) {
        RunParticipantId id = new RunParticipantId(runId, participantId);
        RunParticipant rp = runParticipantRepository.findById(id)
            .orElse(RunParticipant.builder().id(id).build());
        rp.setBoarded(boarded);
        rp.setBoardedAt(boarded ? OffsetDateTime.now() : null);
        return runParticipantRepository.save(rp);
    }
}
