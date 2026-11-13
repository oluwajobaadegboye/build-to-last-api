package com.btl.transport.unit;

import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.notification.NotificationService;
import com.btl.transport.notification.SendGridService;
import com.btl.transport.notification.TwilioService;
import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;
import com.btl.transport.program.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Every outgoing message must describe the participant's own program — never a hardcoded one. */
class ProgramBrandedNotificationsTest {

    private SendGridService sendGrid;
    private ProgramRepository programs;
    private NotificationConfigRepository configs;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        sendGrid = mock(SendGridService.class);
        programs = mock(ProgramRepository.class);
        configs = mock(NotificationConfigRepository.class);
        service = new NotificationService(mock(TwilioService.class), sendGrid, configs, programs);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example.com");
        ReflectionTestUtils.setField(service, "smsEnabled", false);
        // A global "main" config full of another program's text must never be used.
        NotificationConfig main = NotificationConfig.builder()
            .templateRegistration("Hi {{name}}! You're registered for Built to Last 2026")
            .templateRoomAssignment("your room assignment for BTL 2026 is ready").build();
        when(configs.findByConfigKey("main")).thenReturn(Optional.of(main));
    }

    private static Program camp() {
        return Program.builder().id("camp").name("Pinewood Youth Camp")
            .startDate("Jul 5, 2027").endDate("Jul 9, 2027")
            .venue("Pinewood Lodge Hall").venueAddr("12 Pine Rd").city("Boulder").state("CO")
            .airport("Denver International (DEN)").airportPickupsEnabled(false).dailyShuttleEnabled(false)
            .lodgingType("cabin").build();
    }

    private static Program btl() {
        return Program.builder().id("btl2026").name("Built to Last 2026")
            .startDate("Jun 11, 2026").endDate("Jun 14, 2026")
            .venue("Deeper Life Bible Church").venueAddr("4160 Moller Rd").city("Indianapolis").state("IN")
            .airport("Indianapolis International (IND)").lodgingType("hotel").build();
    }

    private Participant participant(String programId, String name) {
        return Participant.builder().programId(programId).fullName(name).email("a@example.com").btlCode("PYC-001").build();
    }

    private record Sent(String sender, String subject, String html, String plain) {}

    private Sent captureHtml() {
        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        verify(sendGrid).sendHtmlEmailAs(sender.capture(), anyString(), anyString(), subject.capture(), html.capture(), plain.capture());
        return new Sent(sender.getValue(), subject.getValue(), html.getValue(), plain.getValue());
    }

    private static void preview(String file, String html) throws Exception {
        Path dir = Path.of("target", "email-previews");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), html);
    }

    @Test
    void registration_email_uses_only_the_camps_own_details() throws Exception {
        when(programs.findById("camp")).thenReturn(Optional.of(camp()));
        when(configs.findByProgramId("camp")).thenReturn(Optional.of(new NotificationConfig()));

        service.sendRegistrationConfirmation(participant("camp", "Casey <Camper>"));
        Sent m = captureHtml();
        preview("registration-camp.html", m.html());

        assertThat(m.sender()).isEqualTo("Pinewood Youth Camp");
        assertThat(m.subject()).isEqualTo("Pinewood Youth Camp — Registration confirmed");
        assertThat(m.html()).contains("Pinewood Youth Camp", "July 5–9, 2027", "Pinewood Lodge Hall, 12 Pine Rd, Boulder, CO",
            "Pinewood Youth Camp Team", "Your registration code", "PYC-001", "your registration details and accommodation");
        assertThat(m.html()).contains("Casey &lt;Camper&gt;").doesNotContain("Casey <Camper>");
        // Pickups are off → no airport line, even though an airport was entered.
        assertThat(m.html()).doesNotContain(">Airport<", "DEN");
        assertThat(m.plain()).contains("You're registered for Pinewood Youth Camp").doesNotContain("{{");
        for (String text : List.of(m.subject(), m.html(), m.plain())) {
            assertThat(text).doesNotContain("Built to Last", "BTL 2026", "Indianapolis", "Deeper Life", "Transport Code");
        }
    }

    @Test
    void btl_registration_email_still_shows_btl_details_from_its_program_record() throws Exception {
        when(programs.findById("btl2026")).thenReturn(Optional.of(btl()));
        when(configs.findByProgramId("btl2026")).thenReturn(Optional.empty());

        service.sendRegistrationConfirmation(participant("btl2026", "Joba"));
        Sent m = captureHtml();
        preview("registration-btl.html", m.html());

        assertThat(m.subject()).isEqualTo("Built to Last 2026 — Registration confirmed");
        assertThat(m.html()).contains("June 11–14, 2026", "Deeper Life Bible Church, 4160 Moller Rd, Indianapolis, IN",
            ">Airport<", "Indianapolis International (IND)", "Built to Last 2026 Team",
            "your registration details, accommodation, airport pickups and daily shuttle schedule");
    }

    @Test
    void program_specific_text_is_used_when_the_program_set_one() {
        when(programs.findById("camp")).thenReturn(Optional.of(camp()));
        when(configs.findByProgramId("camp")).thenReturn(Optional.of(
            NotificationConfig.builder().templateRegistration("Welcome to {{program_name}}, {{name}}!").build()));

        service.sendRegistrationConfirmation(participant("camp", "Casey"));

        assertThat(captureHtml().plain()).isEqualTo("Welcome to Pinewood Youth Camp, Casey!");
    }

    @Test
    void room_assignment_uses_lodging_word_and_program() throws Exception {
        when(programs.findById("camp")).thenReturn(Optional.of(camp()));
        when(configs.findByProgramId("camp")).thenReturn(Optional.empty());

        service.sendRoomAssignment("camp", "Casey", "a@example.com", "Cedar Cabin", "C-1", "double", List.of("Sam"));
        Sent m = captureHtml();
        preview("room-camp.html", m.html());

        assertThat(m.subject()).isEqualTo("Pinewood Youth Camp — Your room assignment");
        assertThat(m.html()).contains(">Cabin<", "Cedar Cabin", "Pinewood Youth Camp Team", "Sam");
        assertThat(m.plain()).contains("Cabin: Cedar Cabin");
        assertThat(m.html() + m.plain()).doesNotContain("BTL 2026", "Built to Last", "Indianapolis", "{{");
    }

    @Test
    void flight_delay_message_is_program_branded_and_fully_filled() {
        when(programs.findById("btl2026")).thenReturn(Optional.of(btl()));
        when(configs.findByProgramId("btl2026")).thenReturn(Optional.empty());

        service.sendDelayNotification(participant("btl2026", "Joba"), "UA77", 45, false);

        Sent m = captureHtml();
        assertThat(m.sender()).isEqualTo("Built to Last 2026");
        assertThat(m.subject()).isEqualTo("Built to Last 2026 — Flight delay update");
        assertThat(m.plain()).contains("UA77", "45 minutes").doesNotContain("{{");
        // The layout signs off once; the SMS-style "— Team" ending isn't repeated in the HTML.
        assertThat(m.html()).contains("UA77", "Thanks,<br>Built to Last 2026 Team").doesNotContain("— Built to Last 2026 Team", "{{");
    }

    @Test
    void messages_without_a_program_use_neutral_wording() {
        when(configs.findByProgramId(any())).thenReturn(Optional.empty());

        service.sendRegistrationConfirmation(participant(null, "Casey"));
        Sent m = captureHtml();

        assertThat(m.sender()).isNull();  // → configured default sender
        assertThat(m.subject()).isEqualTo("Registration confirmed");
        assertThat(m.html()).contains("The Organising Team").doesNotContain("Built to Last", "{{");
    }

    @Test
    void airport_code_is_read_from_the_programs_airport_text() {
        assertThat(ProgramDates.airportCode(Program.builder().airport("Denver International (DEN)").build())).isEqualTo("DEN");
        assertThat(ProgramDates.airportCode(Program.builder().airport("Atlanta Hartsfield-Jackson · atl").build())).isEqualTo("ATL");
        assertThat(ProgramDates.airportCode(Program.builder().airport("ORD").build())).isEqualTo("ORD");
        assertThat(ProgramDates.airportCode(Program.builder().airport("—").build())).isNull();
        assertThat(ProgramDates.airportCode(Program.builder().airport("Some field").build())).isNull();
        assertThat(ProgramDates.airportLabel(Program.builder().build())).isEqualTo("Airport");
    }
}
