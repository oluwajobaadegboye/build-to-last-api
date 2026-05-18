package com.btl.transport.flight;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.participant.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Integer> {

    List<Flight> findByPollingActiveTrue();

    Optional<Flight> findByParticipantAndDirection(Participant participant, Direction direction);

    List<Flight> findByParticipant(Participant participant);

    long countByPollingActiveTrue();
}
