package org.programhub.unit;

import org.programhub.admin.AdminAccess;
import org.programhub.infrastructure.GoogleSheetsClient;
import org.programhub.program.GoogleSheetController;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.program.SheetBackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** One Google Sheet backs up one program: another program can't connect (and overwrite) it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleSheetConnectTest {

    static final String SHEET = "1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-xyz";
    static final String LINK = "https://docs.google.com/spreadsheets/d/" + SHEET + "/edit";

    @Mock ProgramRepository programs;
    @Mock GoogleSheetsClient sheets;
    @Mock SheetBackupService backup;
    @Mock AdminAccess access;
    @InjectMocks GoogleSheetController controller;

    @BeforeEach
    void setUp() {
        when(programs.findById("mine")).thenReturn(Optional.of(Program.builder().id("mine").build()));
        when(sheets.isConfigured()).thenReturn(true);
    }

    @Test
    void a_sheet_already_backing_up_another_program_is_refused() {
        when(programs.existsBySheetIdAndIdNot(SHEET, "mine")).thenReturn(true);
        var e = catchThrowableOfType(
            () -> controller.connect("mine", new GoogleSheetController.ConnectRequest(LINK, null)), ResponseStatusException.class);
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(sheets, never()).open(any());
        verify(programs, never()).save(any());
        verify(backup, never()).sync(any(), anyBoolean());
    }

    @Test
    void a_sheet_not_used_elsewhere_connects() {
        when(programs.existsBySheetIdAndIdNot(SHEET, "mine")).thenReturn(false);
        var state = controller.connect("mine", new GoogleSheetController.ConnectRequest(LINK, null));
        assertThat(state.get("connected")).isEqualTo(true);
        verify(programs).save(argThat(p -> SHEET.equals(p.getSheetId())));
    }
}
