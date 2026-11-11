package com.btl.transport.unit;

import com.btl.transport.admin.AuditCatalog;
import com.btl.transport.admin.AuditCatalog.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCatalogTest {

    private static AuditCatalog.Match m(String method, String path) {
        return AuditCatalog.match(method, "/api/v1/admin" + path);
    }

    @Test
    void describesCommonChangesInPlainWords() {
        assertThat(m("PUT", "/participants/MS-004/check-in"))
            .isEqualTo(new AuditCatalog.Match("checkin", "Changed check-in for", Kind.PARTICIPANT_CODE, "MS-004"));
        assertThat(m("POST", "/participants/MS-004/payments").action()).isEqualTo("Recorded a payment for");
        assertThat(m("DELETE", "/participants/MS-004/payments/12").target()).isEqualTo("MS-004");
        assertThat(m("PATCH", "/participant/MS-004").action()).isEqualTo("Edited");
        assertThat(m("PATCH", "/participant/MS-004/flight/arrival").category()).isEqualTo("transport");
        assertThat(m("DELETE", "/participant/MS-004").action()).isEqualTo("Deleted participant");
        assertThat(m("PATCH", "/registration-fields/7")).isEqualTo(new AuditCatalog.Match("form", "Edited question", Kind.QUESTION, "7"));
        assertThat(m("PUT", "/registration-fields/order").action()).isEqualTo("Reordered registration questions");
        assertThat(m("PUT", "/souvenirs/order").action()).isEqualTo("Reordered souvenir items");
        assertThat(m("PUT", "/participants/MS-004/souvenirs/3").category()).isEqualTo("souvenirs");
        assertThat(m("PATCH", "/programs/abc/policies").action()).isEqualTo("Changed program policies");
        assertThat(m("PATCH", "/programs/abc/roommate-visible").category()).isEqualTo("accommodation");
        assertThat(m("PATCH", "/programs/abc").action()).isEqualTo("Changed program settings");
        assertThat(m("POST", "/upload").action()).isEqualTo("Imported participants from a file");
        assertThat(m("PATCH", "/participants/42/hotel").kind()).isEqualTo(Kind.PARTICIPANT_ID);
        assertThat(m("PUT", "/room-assignments/5/occupants/1").action()).isEqualTo("Assigned someone to");
        assertThat(m("POST", "/run").action()).isEqualTo("Created a trip");
    }

    @Test
    void keepsTheReminderLabelUsedForTheCooldown() {
        assertThat(m("POST", "/payments/reminders").action()).isEqualTo("Sent payment reminders");
    }

    @Test
    void unknownChangesFallBackToOther() {
        assertThat(m("POST", "/something-new")).isEqualTo(new AuditCatalog.Match("other", "Made a change", Kind.NONE, null));
        assertThat(AuditCatalog.match(null, null).category()).isEqualTo("other");
    }
}
