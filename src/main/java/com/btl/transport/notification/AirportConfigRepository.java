package com.btl.transport.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AirportConfigRepository extends JpaRepository<AirportConfig, Integer> {
    Optional<AirportConfig> findByConfigKey(String configKey);
}
