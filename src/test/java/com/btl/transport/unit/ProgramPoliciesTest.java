package com.btl.transport.unit;

import com.btl.transport.admin.AdminPermissions;
import com.btl.transport.payment.PaymentService;
import com.btl.transport.program.Program;
import com.btl.transport.program.RegistrationRules;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Set;

import static com.btl.transport.admin.AdminPermissions.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Registration close date / capacity, amount visibility and the Check-in / overview permissions. */
class ProgramPoliciesTest {

    private static Program prog() {
        return Program.builder().id("p").timezone("America/Chicago").registrationOpen(true).build();
    }

    @Test
    void registration_closes_at_local_time_in_program_zone_or_when_full() {
        Program p = prog();
        OffsetDateTime now = OffsetDateTime.of(2027, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC); // 06:00 Chicago
        assertThat(RegistrationRules.state(p, 0, now)).isEqualTo("open");
        p.setRegistrationClosesAt("2027-02-20T06:30");
        assertThat(RegistrationRules.state(p, 0, now)).isEqualTo("open");
        p.setRegistrationClosesAt("2027-02-20T06:00");
        assertThat(RegistrationRules.state(p, 0, now)).isEqualTo("closed");
        p.setRegistrationClosesAt(null);
        p.setMaxRegistrations(3);
        assertThat(RegistrationRules.state(p, 2, now)).isEqualTo("open");
        assertThat(RegistrationRules.state(p, 3, now)).isEqualTo("full");
        p.setRegistrationOpen(false);
        assertThat(RegistrationRules.state(p, 0, now)).isEqualTo("paused");
    }

    @Test
    void registrations_only_admins_see_amounts_only_when_the_program_allows() {
        Program p = prog();
        assertThat(PaymentService.canSeeAmounts(p, Set.of(REGISTRATIONS))).isTrue();   // default: today's behaviour
        p.setRegistrationsSeeAmounts(false);
        assertThat(PaymentService.canSeeAmounts(p, Set.of(REGISTRATIONS))).isFalse();
        assertThat(PaymentService.canSeeAmounts(p, Set.of(PAYMENTS))).isTrue();
        assertThat(PaymentService.canSeeAmounts(p, Set.of(FULL))).isTrue();
    }

    @Test
    void checkin_and_overview_permissions() {
        String b = "/api/v1/admin";
        assertThat(AdminPermissions.isAllowed(Set.of(CHECKIN), "GET", b + "/check-in")).isTrue();
        assertThat(AdminPermissions.isAllowed(Set.of(CHECKIN), "PUT", b + "/participants/MS-001/check-in")).isTrue();
        assertThat(AdminPermissions.isAllowed(Set.of(CHECKIN), "GET", b + "/participants")).isFalse();
        assertThat(AdminPermissions.isAllowed(Set.of(REGISTRATIONS), "PUT", b + "/participants/MS-001/check-in")).isFalse();
        for (String perm : Set.of(REGISTRATIONS, PAYMENTS, ACCOMMODATION, SOUVENIRS, CHECKIN))
            assertThat(AdminPermissions.isAllowed(Set.of(perm), "GET", b + "/overview")).isTrue();
        assertThat(AdminPermissions.isAllowed(Set.of(PAYMENTS), "POST", b + "/payments/reminders")).isTrue();
        assertThat(AdminPermissions.isAllowed(Set.of(PAYMENTS), "GET", b + "/activity")).isFalse();
        assertThat(AdminPermissions.isAllowed(Set.of(PAYMENTS), "PATCH", b + "/programs/p/policies")).isFalse();
    }
}
