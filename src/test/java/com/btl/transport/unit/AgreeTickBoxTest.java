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

/** Tick-box questions work as "I agree": a required one must be ticked. */
class AgreeTickBoxTest {

    private final RegistrationFieldRepository repo = mock(RegistrationFieldRepository.class);
    private final RegistrationFieldService service = new RegistrationFieldService(repo, new ObjectMapper());

    private void question(boolean required) {
        when(repo.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(
            RegistrationField.builder().programId("p").fieldKey("waiver").label("I agree to the waiver").type("checkbox").required(required).build()));
    }

    @Test
    void required_tick_box_must_be_ticked() {
        question(true);
        assertThat(service.validate("p", Map.of("waiver", true), true)).containsEntry("waiver", true);
        for (Map<String, Object> notTicked : List.<Map<String, Object>>of(Map.of(), Map.of("waiver", false), Map.of("waiver", "false"))) {
            assertThatThrownBy(() -> service.validate("p", notTicked, true))
                .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsKey("waiver"));
        }
        // Admin edits don't enforce it
        assertThat(service.validate("p", Map.of(), false)).doesNotContainKey("waiver");
    }

    @Test
    void optional_tick_box_can_be_left_unticked() {
        question(false);
        assertThat(service.validate("p", Map.of(), true)).doesNotContainKey("waiver");
        assertThat(service.validate("p", Map.of("waiver", false), true)).containsEntry("waiver", false);
    }
}
