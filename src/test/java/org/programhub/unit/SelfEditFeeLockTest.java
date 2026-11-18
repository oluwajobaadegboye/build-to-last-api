package org.programhub.unit;

import org.programhub.admin.AuditLogRepository;
import org.programhub.participant.Participant;
import org.programhub.participant.ParticipantController;
import org.programhub.participant.ParticipantRepository;
import org.programhub.payment.PaymentService;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.registration.RegistrationFieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Attendees editing their own details can't change the answer that sets their fee (e.g. Leader 280 → Youth 200). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfEditFeeLockTest {

    @Mock ParticipantRepository participants;
    @Mock ProgramRepository programs;
    @Mock RegistrationFieldService fields;
    @Mock PaymentService payments;
    @Mock AuditLogRepository audit;
    @InjectMocks ParticipantController controller;

    Participant person;
    Program program;

    @BeforeEach
    void setUp() {
        person = Participant.builder().id(1).programId("p").regCode("ABC-7KQ4M").firstName("Ada").lastName("Obi")
            .fullName("Ada Obi").customAnswers("saved").build();
        program = Program.builder().id("p").attendeeSelfEdit(true)
            .feeQuestionKey("role").feeByOption("{\"Leader\":280,\"Youth\":200}").build();
        when(participants.findByRegCode("ABC-7KQ4M")).thenReturn(Optional.of(person));
        when(programs.findById("p")).thenReturn(Optional.of(program));
        Map<String, Object> saved = new LinkedHashMap<>(Map.of("role", "Leader", "shirt", "M"));
        when(fields.parseAnswers("saved")).thenReturn(saved);
        when(fields.validate(anyString(), any(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        when(fields.toJson(any())).thenReturn("changed");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> answersSaved() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(fields).validate(eq("p"), c.capture(), anyBoolean());
        return c.getValue();
    }

    @Test
    void the_fee_answer_keeps_its_saved_value() {
        controller.updateMyDetails(new ParticipantController.MyDetailsRequest("ABC-7KQ4M", null, null, null,
            Map.of("role", "Youth", "shirt", "L")));
        assertThat(answersSaved()).containsEntry("role", "Leader").containsEntry("shirt", "L");
    }

    @Test
    void other_answers_change_as_before_when_fees_dont_depend_on_answers() {
        program.setFeeByOption(null);
        controller.updateMyDetails(new ParticipantController.MyDetailsRequest("ABC-7KQ4M", null, null, null,
            Map.of("role", "Youth")));
        assertThat(answersSaved()).containsEntry("role", "Youth");
    }
}
