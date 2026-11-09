package com.btl.transport.unit;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.flight.FlightService;
import com.btl.transport.infrastructure.AviationStackClient;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.AirportConfigRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.scheduler.FlightPollingJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Flight tracking follows each program's own dates; the "leave from the venue" rule is opt-in per program. */
class ProgramFlightTrackingTest {

    private static Program program(String id, String start, String end) {
        return Program.builder().id(id).startDate(start).endDate(end).timezone("America/Chicago").build();
    }

    @Test
    void tracking_window_runs_from_day_before_start_to_end_of_day_after_end_in_program_zone() {
        OffsetDateTime[] w = ProgramDates.trackingWindow(program("p", "Mar 1, 2027", "Mar 3, 2027"));
        ZoneId chicago = ZoneId.of("America/Chicago");
        assertThat(w[0].atZoneSameInstant(chicago).toLocalDateTime()).isEqualTo(LocalDateTime.of(2027, 2, 28, 0, 0));
        assertThat(w[1].atZoneSameInstant(chicago).toLocalDateTime()).isEqualTo(LocalDateTime.of(2027, 3, 5, 0, 0));
    }

    @Test
    void no_tracking_without_dates_or_without_airport_pickups() {
        assertThat(ProgramDates.trackingWindow(program("p", null, null))).isNull();
        Program off = program("p", "Mar 1, 2027", "Mar 3, 2027");
        off.setAirportPickupsEnabled(false);
        assertThat(ProgramDates.trackingWindow(off)).isNull();
        assertThat(ProgramDates.isTrackable(off, LocalDate.of(2027, 3, 1), OffsetDateTime.now())).isFalse();
    }

    @Test
    void arrivals_are_trackable_only_on_window_days_and_before_it_closes() {
        Program p = program("p", "Mar 1, 2027", "Mar 3, 2027");
        OffsetDateTime before = OffsetDateTime.of(2027, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        assertThat(ProgramDates.isTrackable(p, LocalDate.of(2027, 2, 28), before)).isTrue();   // day before start
        assertThat(ProgramDates.isTrackable(p, LocalDate.of(2027, 3, 4), before)).isTrue();    // day after end
        assertThat(ProgramDates.isTrackable(p, LocalDate.of(2027, 2, 27), before)).isFalse();
        assertThat(ProgramDates.isTrackable(p, LocalDate.of(2027, 3, 5), before)).isFalse();
        OffsetDateTime after = OffsetDateTime.of(2027, 3, 6, 12, 0, 0, 0, ZoneOffset.UTC);
        assertThat(ProgramDates.isTrackable(p, LocalDate.of(2027, 3, 4), after)).isFalse();    // window over
    }

    @Test
    void venue_departure_rule_is_off_by_default_and_uses_the_programs_time_when_on() {
        Program p = program("p", "Mar 1, 2027", "Mar 3, 2027");
        assertThat(ProgramDates.venueDepartureCutoff(p)).isNull();
        p.setVenueDepartureRule(true);
        assertThat(ProgramDates.venueDepartureCutoff(p)).isEqualTo(LocalTime.of(10, 30));
        p.setVenueDepartureCutoff("09:45");
        assertThat(ProgramDates.venueDepartureCutoff(p)).isEqualTo(LocalTime.of(9, 45));
    }

    @Test
    void polling_job_tracks_only_flights_of_programs_inside_their_window() {
        FlightRepository flights = mock(FlightRepository.class);
        AirportConfigRepository airportConfigs = mock(AirportConfigRepository.class);
        FlightService flightService = mock(FlightService.class);
        AviationStackClient client = mock(AviationStackClient.class);
        ProgramRepository programs = mock(ProgramRepository.class);
        FlightPollingJob job = new FlightPollingJob(flights, airportConfigs, flightService, client, programs);
        ReflectionTestUtils.setField(job, "preArrivalHours", 6);
        ReflectionTestUtils.setField(job, "postArrivalHours", 2);

        LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
        Program running = program("running", today.toString(), today.plusDays(2).toString());
        running.setAirport("Denver International (DEN)");
        Program finished = program("finished", today.minusDays(10).toString(), today.minusDays(8).toString());
        when(programs.findById("running")).thenReturn(Optional.of(running));
        when(programs.findById("finished")).thenReturn(Optional.of(finished));
        when(airportConfigs.findByConfigKey("main")).thenReturn(Optional.of(new AirportConfig()));

        Flight live = flight("UA1", "running", "DEN");
        Flight stale = flight("UA2", "finished", "IND");
        when(flights.findActiveFlightsInWindow(any(), any())).thenReturn(List.of(live, stale));
        when(client.fetchFlight(anyString(), any(), anyBoolean())).thenReturn(Optional.empty());

        job.pollFlights();

        verify(client).fetchFlight("UA1", "DEN", true);
        verify(client, never()).fetchFlight(eq("UA2"), any(), anyBoolean());
        verify(flightService).stopTracking(List.of(stale));
    }

    private static Flight flight(String number, String programId, String airport) {
        Participant p = Participant.builder().programId(programId).fullName("x").build();
        return Flight.builder().flightNumber(number).participant(p).airportCode(airport)
            .direction(Direction.TO_HOTEL).pollingActive(true).build();
    }
}
