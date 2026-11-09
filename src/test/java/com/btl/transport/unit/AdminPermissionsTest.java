package com.btl.transport.unit;

import com.btl.transport.admin.AdminPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.btl.transport.admin.AdminPermissions.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionsTest {

    private static boolean can(Set<String> perms, String method, String path) {
        return AdminPermissions.isAllowed(perms, method, "/api/v1/admin" + path);
    }

    @Test
    void existing_roles_stay_valid_and_full_absorbs_others() {
        assertThat(AdminPermissions.parse("FULL")).containsExactly(FULL);
        assertThat(AdminPermissions.parse("ACCOMMODATION")).containsExactly(ACCOMMODATION);
        assertThat(AdminPermissions.parse("payments, registrations, bogus")).containsExactlyInAnyOrder(PAYMENTS, REGISTRATIONS);
        assertThat(AdminPermissions.parse("REGISTRATIONS,FULL")).containsExactly(FULL);
        assertThat(AdminPermissions.format(List.of("payments", "registrations"))).isEqualTo("REGISTRATIONS,PAYMENTS");
        assertThat(AdminPermissions.format(List.of())).isNull();
        // The former "Paid participants" permission now means Registrations.
        assertThat(AdminPermissions.parse("PAID,ACCOMMODATION")).containsExactlyInAnyOrder(REGISTRATIONS, ACCOMMODATION);
    }

    @Test
    void full_admin_can_do_everything() {
        assertThat(can(Set.of(FULL), "DELETE", "/participant/BL-001")).isTrue();
        assertThat(can(Set.of(FULL), "PATCH", "/config/notifications")).isTrue();
    }

    @Test
    void registrations_viewers_are_read_only() {
        for (Set<String> viewer : List.of(Set.of(REGISTRATIONS))) {
            assertThat(can(viewer, "GET", "/participants")).isTrue();
            assertThat(can(viewer, "GET", "/participant/BL-001")).isTrue();
            assertThat(can(viewer, "GET", "/participants/export.csv")).isTrue();
            assertThat(can(viewer, "GET", "/participants/BL-001/payments")).isTrue();
            assertThat(can(viewer, "PATCH", "/participant/BL-001")).isFalse();
            assertThat(can(viewer, "DELETE", "/participant/BL-001")).isFalse();
            assertThat(can(viewer, "POST", "/participants/BL-001/resend-code")).isFalse();
            assertThat(can(viewer, "POST", "/participants/BL-001/payments")).isFalse();
            assertThat(can(viewer, "GET", "/room-assignments")).isFalse();
            assertThat(can(viewer, "GET", "/users")).isFalse();
        }
    }

    @Test
    void payments_can_record_and_set_status_but_not_edit_participants() {
        Set<String> p = Set.of(PAYMENTS);
        assertThat(can(p, "POST", "/participants/BL-001/payments")).isTrue();
        assertThat(can(p, "DELETE", "/participants/BL-001/payments/3")).isTrue();
        assertThat(can(p, "PATCH", "/participants/BL-001/payment-status")).isTrue();
        assertThat(can(p, "GET", "/participants")).isTrue();
        assertThat(can(p, "PATCH", "/participant/BL-001")).isFalse();
        assertThat(can(p, "GET", "/runs")).isFalse();
    }

    @Test
    void accommodation_manages_rooms_only() {
        Set<String> a = Set.of(ACCOMMODATION);
        assertThat(can(a, "GET", "/room-assignments")).isTrue();
        assertThat(can(a, "POST", "/room-assignments/import")).isTrue();
        assertThat(can(a, "PATCH", "/accommodation-contacts/2")).isTrue();
        assertThat(can(a, "PATCH", "/programs/p1/roommate-visible")).isTrue();
        assertThat(can(a, "PATCH", "/programs/p1")).isTrue();      // hotels only — enforced in the controller
        assertThat(can(a, "DELETE", "/programs/p1")).isFalse();
        assertThat(can(a, "GET", "/participants")).isFalse();
        assertThat(can(a, "POST", "/users")).isFalse();
    }

    @Test
    void combinations_add_up() {
        Set<String> combo = Set.of(REGISTRATIONS, ACCOMMODATION);
        assertThat(can(combo, "GET", "/participants")).isTrue();
        assertThat(can(combo, "GET", "/room-assignments")).isTrue();
        assertThat(can(combo, "POST", "/participants/BL-001/payments")).isFalse();
        assertThat(AdminPermissions.label(combo)).isEqualTo("Registrations, Accommodation");
    }
}
