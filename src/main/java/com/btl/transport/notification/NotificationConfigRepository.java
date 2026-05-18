package com.btl.transport.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Integer> {
    Optional<NotificationConfig> findByConfigKey(String configKey);
}
