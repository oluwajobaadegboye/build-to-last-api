package org.programhub.program;

import org.programhub.admin.AdminAccess;
import org.programhub.admin.Audit;
import org.programhub.infrastructure.GoogleSheetsClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Google Sheets backup settings for a program (full admins). */
@Tag(name = "Google Sheets backup", description = "Mirror a program's participants to a Google Sheet shared with ProgramHub")
@RestController
@RequestMapping("/api/v1/admin/programs/{id}/google-sheet")
@RequiredArgsConstructor
public class GoogleSheetController {

    private final ProgramRepository programRepository;
    private final GoogleSheetsClient sheets;
    private final SheetBackupService backup;
    private final AdminAccess adminAccess;

    public record ConnectRequest(@JsonProperty("sheet_url") String sheetUrl, String tab) {}

    private Program program(String id) {
        adminAccess.requireProgram(id);
        return programRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    private Map<String, Object> state(Program p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", sheets.isConfigured());
        m.put("service_account_email", sheets.serviceAccountEmail());
        m.put("connected", p.getSheetId() != null);
        m.put("sheet_url", p.getSheetId() != null ? "https://docs.google.com/spreadsheets/d/" + p.getSheetId() + "/edit" : null);
        m.put("sheet_title", p.getSheetTitle());
        m.put("tab", p.getSheetTab() != null ? p.getSheetTab() : SheetBackupService.DEFAULT_TAB);
        m.put("synced_at", p.getSheetSyncedAt() != null ? p.getSheetSyncedAt().toString() : null);
        m.put("error", p.getSheetError());
        return m;
    }

    @Operation(summary = "Google Sheets backup status")
    @GetMapping
    public Map<String, Object> get(@PathVariable String id) {
        return state(program(id));
    }

    @Operation(summary = "Connect a Google Sheet", description = "Checks ProgramHub can edit it, then writes every participant to the tab")
    @PutMapping
    public Map<String, Object> connect(@PathVariable String id, @RequestBody ConnectRequest req) {
        Program p = program(id);
        if (!sheets.isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google Sheets backup isn't set up on this server yet");
        String sheetId = GoogleSheetsClient.sheetId(req.sheetUrl());
        if (sheetId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paste the link to a Google Sheet (docs.google.com/spreadsheets/…)");
        String tab = req.tab() == null || req.tab().isBlank() ? SheetBackupService.DEFAULT_TAB : req.tab().trim();
        if (tab.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the tab name under 100 characters");
        if (programRepository.existsBySheetIdAndIdNot(sheetId, p.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That sheet is already backing up another program. Use a new sheet for this one.");
        try {
            sheets.open(sheetId);  // fails now (with a clear message) if it isn't shared with us
        } catch (GoogleSheetsClient.SheetsException e) {
            throw new ResponseStatusException(e.status == 403 || e.status == 404 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY, backup.explain(e));
        }
        p.setSheetId(sheetId);
        p.setSheetTab(tab);
        p.setSheetError(null);
        programRepository.save(p);
        backup.reset(p.getId());
        backup.sync(p, true);
        Audit.detail("Google Sheet connected" + (p.getSheetTitle() != null ? " · " + p.getSheetTitle() : ""));
        return state(p);
    }

    @Operation(summary = "Sync now", description = "Rewrites the sheet with every participant")
    @PostMapping("/sync")
    public Map<String, Object> syncNow(@PathVariable String id) {
        Program p = program(id);
        if (p.getSheetId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connect a Google Sheet first");
        backup.sync(p, true);
        return state(p);
    }

    @Operation(summary = "Disconnect the Google Sheet", description = "Stops updating it; the sheet and its data stay in Google Drive")
    @DeleteMapping
    public Map<String, Object> disconnect(@PathVariable String id) {
        Program p = program(id);
        p.setSheetId(null);
        p.setSheetTitle(null);
        p.setSheetSyncedAt(null);
        p.setSheetError(null);
        programRepository.save(p);
        backup.reset(p.getId());
        return state(p);
    }
}
