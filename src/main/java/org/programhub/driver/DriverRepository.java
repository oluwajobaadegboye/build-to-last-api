package org.programhub.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Integer> {
    List<Driver> findByProgramId(String programId);
    Optional<Driver> findByLoginToken(String loginToken);
    Optional<Driver> findByDriverCode(String driverCode);
}
