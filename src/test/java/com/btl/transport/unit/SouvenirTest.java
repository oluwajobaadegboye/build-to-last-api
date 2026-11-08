package com.btl.transport.unit;

import com.btl.transport.admin.AdminPermissions;
import com.btl.transport.participant.Participant;
import com.btl.transport.registration.RegistrationFieldService.InvalidAnswersException;
import com.btl.transport.souvenir.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.btl.transport.admin.AdminPermissions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Souvenir sizes at registration, hand-out updates and the Souvenirs permission. */
class SouvenirTest {

    private final SouvenirItemRepository items = mock(SouvenirItemRepository.class);
    private final ParticipantSouvenirRepository records = mock(ParticipantSouvenirRepository.class);
    private final SouvenirService service = new SouvenirService(items, records, new ObjectMapper());

    private static SouvenirItem item(int id, String name, String options, boolean ask) {
        return SouvenirItem.builder().id(id).programId("p").name(name).options(options).askAtRegistration(ask).build();
    }

    @Test
    void only_items_with_options_marked_ask_are_required_at_registration() {
        when(items.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc("p")).thenReturn(List.of(
            item(1, "T-shirt", "[\"S\",\"M\",\"L\"]", true),
            item(2, "Cap", null, true),                    // no options → never asked
            item(3, "Hoodie", "[\"M\",\"L\"]", false)));   // not asked
        assertThat(service.registrationItems("p")).extracting(m -> m.get("name")).containsExactly("T-shirt");

        assertThat(service.validateChoices("p", Map.of("1", " M ", "99", "x"))).containsExactly(Map.entry(1, "M"));

        assertThatThrownBy(() -> service.validateChoices("p", Map.of()))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors()).containsKey("souvenir:1"));
        assertThatThrownBy(() -> service.validateChoices("p", Map.of("1", "XXL")))
            .isInstanceOfSatisfying(InvalidAnswersException.class, e -> assertThat(e.getErrors().get("souvenir:1")).contains("listed options"));
    }

    @Test
    void handing_out_records_who_and_when_and_undo_clears_it() {
        Participant p = Participant.builder().id(7).programId("p").fullName("A").build();
        SouvenirItem shirt = item(1, "T-shirt", "[\"S\",\"M\"]", true);
        ParticipantSouvenir existing = ParticipantSouvenir.builder().participantId(7).itemId(1).programId("p").choice("S").build();
        when(records.findByParticipantIdAndItemId(7, 1)).thenReturn(Optional.of(existing));
        when(records.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> given = service.update(p, shirt, true, "M", "desk1");
        assertThat(given).containsEntry("received", true).containsEntry("choice", "M").containsEntry("received_by", "desk1");
        assertThat(given.get("received_at")).isNotNull();

        Map<String, Object> undone = service.update(p, shirt, false, null, "desk1");
        assertThat(undone).containsEntry("received", false).containsEntry("choice", "M").containsEntry("received_by", null);

        assertThatThrownBy(() -> service.update(p, shirt, null, "XL", "desk1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void souvenirs_permission_covers_only_souvenirs() {
        Set<String> s = Set.of(SOUVENIRS);
        String base = "/api/v1/admin";
        assertThat(AdminPermissions.isAllowed(s, "GET", base + "/souvenirs/overview")).isTrue();
        assertThat(AdminPermissions.isAllowed(s, "POST", base + "/souvenirs")).isTrue();
        assertThat(AdminPermissions.isAllowed(s, "PATCH", base + "/souvenirs/3")).isTrue();
        assertThat(AdminPermissions.isAllowed(s, "PUT", base + "/participants/MS-001/souvenirs/3")).isTrue();
        assertThat(AdminPermissions.isAllowed(s, "GET", base + "/participants")).isFalse();
        assertThat(AdminPermissions.isAllowed(s, "POST", base + "/participants/MS-001/payments")).isFalse();
        assertThat(AdminPermissions.isAllowed(Set.of(REGISTRATIONS, PAYMENTS, ACCOMMODATION), "PUT", base + "/participants/MS-001/souvenirs/3")).isFalse();
        assertThat(AdminPermissions.label(Set.of(SOUVENIRS, PAYMENTS))).isEqualTo("Payments, Souvenirs");
    }
}
