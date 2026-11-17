package org.programhub.notification;

import org.programhub.common.enums.ConferenceDay;
import org.programhub.common.enums.Direction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShuttleConfigRepository extends JpaRepository<ShuttleConfig, Integer> {
    List<ShuttleConfig> findByConferenceDay(ConferenceDay day);
    Optional<ShuttleConfig> findByConferenceDayAndDirection(ConferenceDay day, Direction direction);
}
