package com.btl.transport.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Lets a timed job run on only one server copy per round. The first copy to claim the job holds it for
 * {@code hold} (a little less than the job's interval); the other copy sees it taken and skips that round.
 * The hold isn't released early on purpose: the other copy fires a moment later and must still skip.
 * If the copy holding it stops, the hold simply runs out and the next round runs elsewhere.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobLock {

    private final JdbcTemplate jdbc;
    private final String owner = hostName() + "/" + UUID.randomUUID().toString().substring(0, 8);

    /** True when this copy should run {@code job} now. */
    public boolean acquire(String job, Duration hold) {
        try {
            int claimed = jdbc.update(
                "INSERT INTO job_locks (name, locked_until, locked_by) VALUES (?, now() + make_interval(secs => ?), ?) " +
                "ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until, locked_by = EXCLUDED.locked_by " +
                "WHERE job_locks.locked_until <= now()",
                job, (double) hold.toSeconds(), owner);
            return claimed == 1;
        } catch (Exception e) {
            // Database trouble: skip this round rather than risk running twice; the next round tries again.
            log.warn("Couldn't claim job {}: {}", job, e.getMessage());
            return false;
        }
    }

    private static String hostName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "server"; }
    }
}
