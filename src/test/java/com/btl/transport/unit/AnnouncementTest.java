package com.btl.transport.unit;

import com.btl.transport.admin.AdminPermissions;
import com.btl.transport.notification.EmailLayout;
import com.btl.transport.notification.ProgramBranding;
import com.btl.transport.program.Program;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Announcement emails: the organiser's formatting, nothing unsafe; and who may send them. */
class AnnouncementTest {

    private static String render(String text) {
        Program p = Program.builder().id("p").name("Youth Camp").build();
        return EmailLayout.forProgram(ProgramBranding.of(p)).richText(text).html();
    }

    @Test
    void formatting_becomes_email_html() {
        String html = render("**Bus leaves at 8**, be *early*.\n\n- Bring water\n- Bring a hat\n\nMap: https://maps.example.com/camp and [the schedule](https://example.com/s)");
        assertThat(html).contains("<strong>Bus leaves at 8</strong>").contains("<em>early</em>");
        assertThat(html).contains("<ul").contains(">Bring water</li>").contains(">Bring a hat</li>");
        assertThat(html).contains("href=\"https://maps.example.com/camp\"").contains(">maps.example.com/camp</a>");
        assertThat(html).contains("href=\"https://example.com/s\"").contains(">the schedule</a>");
    }

    @Test
    void html_and_scripts_in_the_message_are_shown_as_text() {
        String html = render("<script>alert(1)</script> <b onclick=x>hi</b> [x](javascript:alert(1))");
        assertThat(html).doesNotContain("<script>").doesNotContain("onclick=x>").doesNotContain("href=\"javascript");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void messaging_permission_covers_announcements_only() {
        Set<String> messaging = Set.of(AdminPermissions.MESSAGING);
        assertThat(AdminPermissions.isAllowed(messaging, "POST", "/api/v1/admin/announcements")).isTrue();
        assertThat(AdminPermissions.isAllowed(messaging, "GET", "/api/v1/admin/announcements/options")).isTrue();
        assertThat(AdminPermissions.isAllowed(messaging, "GET", "/api/v1/admin/participants")).isFalse();
        assertThat(AdminPermissions.isAllowed(Set.of(AdminPermissions.CHECKIN), "POST", "/api/v1/admin/announcements")).isFalse();
        assertThat(AdminPermissions.label(messaging)).isEqualTo("Announcements");
    }
}
