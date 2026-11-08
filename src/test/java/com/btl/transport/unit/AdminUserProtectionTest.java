package com.btl.transport.unit;

import com.btl.transport.admin.*;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Admin account protections (accounts are shared across programs):
 *  - nobody gets Full access by default;
 *  - another admin's sign-in details can only be changed by someone who fully runs every program they're in;
 *  - a program's creator can't be removed, demoted or have their sign-in details changed by other admins.
 *
 * Setup: owner (1) created X and Y and is Full in both. Co-admin (2) is Full in X only.
 * Volunteer (3) is Registrations in X only. Shared (4) is Registrations in X and Y.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserProtectionTest {

    @Mock AdminUserRepository users;
    @Mock ProgramAdminRepository memberships;
    @Mock ProgramRepository programs;
    @Mock AdminAccess access;
    @Mock AdminProperties props;
    @InjectMocks AdminController controller;

    static final String X = "prog-x", Y = "prog-y";
    final Map<Integer, AdminUser> accounts = new HashMap<>();
    final List<ProgramAdmin> all = new ArrayList<>();

    private void account(int id, String name) {
        accounts.put(id, AdminUser.builder().id(id).username(name).passwordHash("hash-" + id).displayName(name).email(name + "@x.org").role("FULL").build());
    }

    private void member(int id, String program, String role) {
        all.add(ProgramAdmin.builder().id(all.size() + 1).adminUserId(id).programId(program).role(role).build());
    }

    @BeforeEach
    void setUp() {
        account(1, "owner"); account(2, "coadmin"); account(3, "volunteer"); account(4, "shared");
        member(1, X, "FULL"); member(1, Y, "FULL"); member(2, X, "FULL"); member(3, X, "REGISTRATIONS");
        member(4, X, "REGISTRATIONS"); member(4, Y, "REGISTRATIONS");

        when(programs.findById(X)).thenReturn(Optional.of(Program.builder().id(X).createdByAdminId(1).build()));
        when(programs.findById(Y)).thenReturn(Optional.of(Program.builder().id(Y).createdByAdminId(1).build()));
        when(users.findById(any())).thenAnswer(i -> Optional.ofNullable(accounts.get((Integer) i.getArgument(0))));
        when(users.findAll()).thenAnswer(i -> new ArrayList<>(accounts.values()));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(memberships.findByAdminUserId(any())).thenAnswer(i -> all.stream().filter(m -> m.getAdminUserId().equals(i.getArgument(0))).toList());
        when(memberships.findByAdminUserIdAndProgramId(any(), any())).thenAnswer(i -> all.stream()
            .filter(m -> m.getAdminUserId().equals(i.getArgument(0)) && m.getProgramId().equals(i.getArgument(1))).findFirst());
        when(memberships.findByProgramIdOrderByCreatedAtAsc(any())).thenAnswer(i -> all.stream().filter(m -> m.getProgramId().equals(i.getArgument(0))).toList());
        when(memberships.countByProgramId(any())).thenAnswer(i -> all.stream().filter(m -> m.getProgramId().equals(i.getArgument(0))).count());
        when(props.getUsers()).thenReturn(List.of());
        when(access.isSuper()).thenReturn(false);
    }

    private void actingAs(int id) {
        when(access.currentUserId()).thenReturn(id);
        when(access.currentUsername()).thenReturn(accounts.get(id).getUsername());
    }

    private ResponseEntity<Map<String, Object>> update(int target, String username, String name, String password, String email, List<String> perms) {
        return controller.updateAdminUser(X, target, new AdminController.UpdateAdminUserRequest(username, name, password, null, email, perms));
    }

    // ── No Full access by default ───────────────────────────────────────────

    @Test
    void creating_an_admin_without_a_valid_permission_is_refused() {
        actingAs(1);
        for (List<String> perms : Arrays.<List<String>>asList(List.of(), List.of("GOD"), null)) {
            var res = controller.createAdminUser(X, new AdminController.CreateAdminUserRequest("new_" + perms, "password123", null, null, null, perms));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(users, never()).save(any());
        verify(memberships, never()).save(any());
    }

    @Test
    void editing_an_admin_to_no_valid_permission_is_refused() {
        actingAs(1);
        assertThat(update(3, null, null, null, null, List.of("nope")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(all.stream().filter(m -> m.getAdminUserId() == 3).findFirst().orElseThrow().getRole()).isEqualTo("REGISTRATIONS");
    }

    // ── Sign-in details across programs ─────────────────────────────────────

    @Test
    void cannot_change_sign_in_details_of_someone_in_a_program_you_dont_run() {
        actingAs(2);  // Full in X only; "shared" is also in Y
        for (var res : List.of(
                update(4, null, null, "hacked123", null, null),
                update(4, "renamed", null, null, null, null),
                update(4, null, null, null, "evil@x.org", null),
                update(4, null, "Evil", null, null, null))) {
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        AdminUser shared = accounts.get(4);
        assertThat(shared.getPasswordHash()).isEqualTo("hash-4");
        assertThat(shared.getUsername()).isEqualTo("shared");
        assertThat(shared.getEmail()).isEqualTo("shared@x.org");
    }

    @Test
    void can_still_change_permissions_of_someone_in_a_program_you_dont_fully_run_elsewhere() {
        actingAs(2);
        // The form re-sends unchanged details; only the permission differs.
        var res = update(4, "shared", "shared", null, "shared@x.org", List.of("PAYMENTS"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void can_reset_password_of_someone_only_in_programs_you_run() {
        actingAs(2);  // volunteer is only in X, where the co-admin is Full
        assertThat(update(3, null, null, "newpass123", null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.get(3).getPasswordHash()).isNotEqualTo("hash-3");
    }

    @Test
    void anyone_can_change_their_own_details() {
        actingAs(4);
        assertThat(update(4, null, "Shared Person", null, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.get(4).getDisplayName()).isEqualTo("Shared Person");
    }

    // ── Program creator ─────────────────────────────────────────────────────

    @Test
    void other_admins_cannot_remove_the_creator() {
        actingAs(2);
        var res = controller.deleteAdminUser(X, 1);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(memberships, never()).delete(any());
    }

    @Test
    void other_admins_cannot_demote_or_reset_the_creator() {
        actingAs(2);
        assertThat(update(1, null, null, null, null, List.of("REGISTRATIONS")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(update(1, null, null, "takeover1", null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(update(1, null, "Evil", null, null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(accounts.get(1).getPasswordHash()).isEqualTo("hash-1");
        assertThat(all.stream().filter(m -> m.getAdminUserId() == 1 && m.getProgramId().equals(X)).findFirst().orElseThrow().getRole()).isEqualTo("FULL");
        // Saving the creator unchanged (same permissions, same details) is fine.
        assertThat(update(1, "owner", "owner", null, "owner@x.org", List.of("FULL")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void the_creator_can_manage_themselves_and_others() {
        actingAs(1);
        assertThat(update(1, null, "The Owner", null, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.deleteAdminUser(X, 3).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(memberships).delete(argThatMember(3, X));
    }

    @Test
    void programs_without_a_recorded_creator_protect_nobody_extra() {
        when(programs.findById(X)).thenReturn(Optional.of(Program.builder().id(X).createdByAdminId(null).build()));
        actingAs(2);
        // Owner is still Full in Y (which co-admin doesn't run), so their sign-in details stay protected…
        assertThat(update(1, null, null, "takeover1", null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // …but they can be removed from X, as another Full admin remains.
        assertThat(controller.deleteAdminUser(X, 1).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static ProgramAdmin argThatMember(int userId, String program) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAdminUserId() == userId && program.equals(m.getProgramId()));
    }
}
