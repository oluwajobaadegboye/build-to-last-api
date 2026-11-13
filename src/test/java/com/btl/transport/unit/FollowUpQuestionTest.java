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

/** Follow-up questions: shown (and required) only when an earlier choice question has a trigger answer. */
class FollowUpQuestionTest {

    private final RegistrationFieldRepository repo = mock(RegistrationFieldRepository.class);
    private final RegistrationFieldService service = new RegistrationFieldService(repo, new ObjectMapper());

    private static RegistrationField diet(String type) {
        return RegistrationField.builder().programId("p").fieldKey("diet").label("Dietary needs").type(type)
            .options("[\"None\",\"Vegetarian\",\"Other\"]").required(true).build();
    }
    private static RegistrationField describe() {
        return RegistrationField.builder().programId("p").fieldKey("diet_other").label("Please describe").type("text")
            .required(true).showIfKey("diet").showIfValues("[\"Other\"]").build();
    }
    private void questions(RegistrationField... fs) {
        when(repo.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(fs));
    }

    @Test
    void follow_up_is_required_when_its_answer_is_picked() {
        questions(diet("radio"), describe());
        assertThatThrownBy(() -> service.validate("p", Map.of("diet", "Other"), true))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("diet_other"));
        assertThat(service.validate("p", Map.of("diet", "Other", "diet_other", " nuts "), true))
            .containsEntry("diet_other", "nuts");
    }

    @Test
    void hidden_follow_up_is_not_required_and_its_answer_is_dropped() {
        questions(diet("select"), describe());
        assertThat(service.validate("p", Map.of("diet", "None"), true)).containsOnlyKeys("diet");
        assertThat(service.validate("p", Map.of("diet", "None", "diet_other", "stale"), true)).doesNotContainKey("diet_other");
    }

    @Test
    void pick_several_shows_follow_up_when_any_trigger_is_ticked() {
        questions(diet("multiselect"), describe());
        assertThatThrownBy(() -> service.validate("p", Map.of("diet", List.of("Vegetarian", "Other")), true))
            .isInstanceOf(InvalidAnswersException.class);
        assertThat(service.validate("p", Map.of("diet", List.of("Vegetarian")), true)).doesNotContainKey("diet_other");
    }

    @Test
    void follow_up_of_a_removed_question_is_hidden() {
        questions(describe()); // "diet" no longer active
        assertThat(service.validate("p", Map.of(), true)).isEmpty();
    }

    @Test
    void chained_follow_up_is_hidden_when_its_parent_is_hidden() {
        RegistrationField severity = RegistrationField.builder().programId("p").fieldKey("severity").label("How severe?")
            .type("radio").options("[\"Mild\",\"Severe\"]").required(true).showIfKey("diet").showIfValues("[\"Other\"]").build();
        RegistrationField epipen = RegistrationField.builder().programId("p").fieldKey("epipen").label("Do you carry an EpiPen?")
            .type("text").required(true).showIfKey("severity").showIfValues("[\"Severe\"]").build();
        questions(diet("radio"), severity, epipen);
        // Severity stale-answered "Severe" but diet isn't Other → both hidden.
        assertThat(service.validate("p", Map.of("diet", "None", "severity", "Severe"), true)).containsOnlyKeys("diet");
        assertThatThrownBy(() -> service.validate("p", Map.of("diet", "Other", "severity", "Severe"), true))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("epipen"));
    }

    @Test
    void admin_edits_do_not_check_conditions() {
        questions(diet("radio"), describe());
        assertThat(service.validate("p", Map.of("diet_other", "set by admin"), false)).containsEntry("diet_other", "set by admin");
    }

    @Test
    void dto_includes_the_condition() {
        assertThat(service.toDto(describe()).get("show_if")).isEqualTo(Map.of("key", "diet", "values", List.of("Other")));
        assertThat(service.toDto(diet("radio")).get("show_if")).isNull();
    }
}
