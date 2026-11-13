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

/** Phone and email questions (e.g. an emergency contact) must hold a real-looking number or address. */
class ContactQuestionTest {

    private final RegistrationFieldRepository repo = mock(RegistrationFieldRepository.class);
    private final RegistrationFieldService service = new RegistrationFieldService(repo, new ObjectMapper());

    private void question(String type) {
        when(repo.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(
            RegistrationField.builder().programId("p").fieldKey("c").label("Contact").type(type).required(true).build()));
    }

    private void rejects(Object value) {
        assertThatThrownBy(() -> service.validate("p", Map.of("c", value), true))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsKey("c"));
    }

    @Test
    void phone_is_stored_in_international_form() {
        question("phone");
        assertThat(service.validate("p", Map.of("c", "+1 (641) 819-1032"), true)).containsEntry("c", "+16418191032");
        rejects("6418191032");   // no country code
        rejects("+12");
        rejects("call me");
    }

    @Test
    void email_must_look_like_an_address() {
        question("email");
        assertThat(service.validate("p", Map.of("c", " ada@example.com "), true)).containsEntry("c", "ada@example.com");
        rejects("ada@example");
        rejects("ada example.com");
        rejects("a b@example.com");
    }

    @Test
    void both_are_known_types() {
        assertThat(RegistrationFieldService.TYPES).contains("phone", "email");
        assertThat(RegistrationFieldService.isChoice("phone")).isFalse();
    }
}
