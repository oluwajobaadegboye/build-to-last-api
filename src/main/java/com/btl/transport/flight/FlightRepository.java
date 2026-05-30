package com.btl.transport.flight;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.participant.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Integer> {

    List<Flight> findByPollingActiveTrue();

    @Query("SELECT f FROM Flight f WHERE f.pollingActive = true " +
           "AND f.submittedDatetime >= :windowStart AND f.submittedDatetime <= :windowEnd")
    List<Flight> findActiveFlightsInWindow(
        @Param("windowStart") OffsetDateTime windowStart,
        @Param("windowEnd") OffsetDateTime windowEnd
    );

    @Query("SELECT f FROM Flight f WHERE f.pollingActive = true AND f.submittedDatetime < :startOfToday")
    List<Flight> findStaleActiveFlights(@Param("startOfToday") OffsetDateTime startOfToday);

    Optional<Flight> findByParticipantAndDirection(Participant participant, Direction direction);

    List<Flight> findByParticipant(Participant participant);

    List<Flight> findByParticipantIn(List<Participant> participants);

    long countByPollingActiveTrue();
}
