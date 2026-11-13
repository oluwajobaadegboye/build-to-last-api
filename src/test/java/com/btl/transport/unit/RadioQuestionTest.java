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

/** "Multiple choice (pick one)" questions: the answer must be exactly one of the options. */
class RadioQuestionTest {

    private final RegistrationFieldRepository repo = mock(RegistrationFieldRepository.class);
    private final RegistrationFieldService service = new RegistrationFieldService(repo, new ObjectMapper());

    private void question(boolean required) {
        when(repo.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(
            RegistrationField.builder().programId("p").fieldKey("meal").label("Meal").type("radio")
                .options("[\"Rice\",\"Pasta\"]").required(required).build()));
    }

    @Test
    void radio_is_a_choice_type() {
        assertThat(RegistrationFieldService.TYPES).contains("radio");
        assertThat(RegistrationFieldService.isChoice("radio")).isTrue();
        assertThat(RegistrationFieldService.isChoice("text")).isFalse();
    }

    @Test
    void answer_must_be_one_listed_option() {
        question(true);
        assertThat(service.validate("p", Map.of("meal", " Rice "), true)).containsEntry("meal", "Rice");
        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of(), Map.of("meal", "Soup"), Map.of("meal", ""))) {
            assertThatThrownBy(() -> service.validate("p", bad, true))
                .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsKey("meal"));
        }
    }

    @Test
    void optional_radio_can_be_skipped() {
        question(false);
        assertThat(service.validate("p", Map.of(), true)).doesNotContainKey("meal");
    }
}
