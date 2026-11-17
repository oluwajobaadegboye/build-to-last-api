package com.btl.transport.program;

import com.btl.transport.infrastructure.GoogleSheetsClient;
import com.btl.transport.registration.ParticipantExportController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each connected program's Google Sheet a live copy of its participants (the same columns as the
 * Participants CSV). Checked every minute; the sheet is only rewritten when something changed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SheetBackupService {

    public static final String DEFAULT_TAB = "Participants";
    private static final Duration RETRY_AFTER_ERROR = Duration.ofMinutes(10);

    private final GoogleSheetsClient sheets;
    private final ProgramRepository programRepository;
    private final ParticipantExportController export;

    /** Only one server copy runs each round (absent in unit tests, which build the job directly). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.btl.transport.scheduler.JobLock jobLock;

    /** Last content written per program (hash), so unchanged programs aren't rewritten. */
    private final Map<String, Integer> lastWritten = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastFailure = new ConcurrentHashMap<>();

    /** A friendly message for an error from Google. */
    public String explain(GoogleSheetsClient.SheetsException e) {
        return switch (e.status) {
            case 403 -> "ProgramHub can't edit this sheet. Share it with " + sheets.serviceAccountEmail() + " as Editor.";
            case 404 -> "That sheet couldn't be found. Check the link.";
            case 503 -> "Google Sheets backup isn't set up on this server yet.";
            default -> "Google Sheets said: " + e.getMessage();
        };
    }

    /** Writes the program's participants to its sheet now (when {@code force} or changed). Records success or the error. */
    public synchronized void sync(Program p, boolean force) {
        if (p.getSheetId() == null) return;
        String tab = p.getSheetTab() != null && !p.getSheetTab().isBlank() ? p.getSheetTab() : DEFAULT_TAB;
        try {
            List<List<String>> rows = export.table(p.getId());
            int hash = (p.getSheetId() + "|" + tab + "|" + rows).hashCode();
            if (!force && Integer.valueOf(hash).equals(lastWritten.get(p.getId()))) return;
            var info = sheets.open(p.getSheetId());
            sheets.ensureTab(p.getSheetId(), tab, info.tabs());
            sheets.replace(p.getSheetId(), tab, rows);
            lastWritten.put(p.getId(), hash);
            lastFailure.remove(p.getId());
            p.setSheetTitle(info.title());
            p.setSheetSyncedAt(OffsetDateTime.now());
            p.setSheetError(null);
        } catch (GoogleSheetsClient.SheetsException e) {
            lastFailure.put(p.getId(), OffsetDateTime.now());
            p.setSheetError(explain(e));
            log.warn("Google Sheets backup failed for {}: {} {}", p.getId(), e.status, e.getMessage());
        } catch (Exception e) {
            lastFailure.put(p.getId(), OffsetDateTime.now());
            p.setSheetError("Backup failed — we'll try again shortly.");
            log.warn("Google Sheets backup failed for {}", p.getId(), e);
        }
        programRepository.save(p);
    }

    /** Forgets what was written, so the next sync rewrites the sheet (after connecting or changing the tab). */
    public void reset(String programId) {
        lastWritten.remove(programId);
        lastFailure.remove(programId);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void syncAll() {
        if (!sheets.isConfigured()) return;
        if (jobLock != null && !jobLock.acquire("google-sheets-backup", java.time.Duration.ofSeconds(50))) return;
        for (Program p : programRepository.findBySheetIdIsNotNull()) {
            OffsetDateTime failed = lastFailure.get(p.getId());
            if (failed != null && failed.isAfter(OffsetDateTime.now().minus(RETRY_AFTER_ERROR))) continue;
            sync(p, false);
        }
    }
}
