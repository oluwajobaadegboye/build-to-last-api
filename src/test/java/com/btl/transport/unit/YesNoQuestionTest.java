package com.btl.transport.unit;

import com.btl.transport.registration.RegistrationField;
import com.btl.transport.registration.RegistrationFieldRepository;
import com.btl.transport.registration.RegistrationFieldService;
import com.btl.transport.registration.RegistrationFieldService.InvalidAnswersException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Yes / No questions: attendees answer Yes or No; "required" means they must pick one. */
class YesNoQuestionTest {

    private final RegistrationFieldRepository repo = mock(RegistrationFieldRepository.class);
    private final RegistrationFieldService service = new RegistrationFieldService(repo, new ObjectMapper());

    private void questions(boolean required) {
        when(repo.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(
            RegistrationField.builder().programId("p").fieldKey("photos").label("Photo consent").type("checkbox").required(required).build()));
    }

    @Test
    void yes_and_no_are_both_saved() {
        questions(true);
        assertThat(service.validate("p", Map.of("photos", true), true)).containsEntry("photos", true);
        assertThat(service.validate("p", Map.of("photos", false), true)).containsEntry("photos", false);
        assertThat(service.validate("p", Map.of("photos", "false"), true)).containsEntry("photos", false);
    }

    @Test
    void required_question_must_be_answered() {
        questions(true);
        assertThatThrownBy(() -> service.validate("p", Map.of(), true))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsKey("photos"));
        // Admin edits may leave it blank
        assertThat(service.validate("p", Map.of(), false)).doesNotContainKey("photos");
    }

    @Test
    void optional_question_can_be_skipped_and_junk_is_refused() {
        questions(false);
        assertThat(service.validate("p", Map.of(), true)).doesNotContainKey("photos");
        assertThatThrownBy(() -> service.validate("p", Map.of("photos", "maybe"), true))
            .isInstanceOf(InvalidAnswersException.class);
    }
}
