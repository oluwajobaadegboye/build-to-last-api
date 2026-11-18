package org.programhub.unit;

import org.programhub.program.FeeRules;
import org.programhub.program.Program;
import org.programhub.program.RoomVisibility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeeRulesTest {

    private static Program camp() {
        Program p = new Program();
        p.setRegistrationFee(new BigDecimal("200"));
        p.setFeeQuestionKey("age");
        p.setFeeByOption("{\"Leader\":280,\"Chaperone\":200,\"Guest\":0}");
        return p;
    }

    @Test
    void the_answer_sets_the_fee_and_everyone_else_pays_the_base() {
        Program p = camp();
        assertThat(FeeRules.feeFor(p, Map.of("age", "Leader"))).isEqualByComparingTo("280");
        assertThat(FeeRules.feeFor(p, Map.of("age", "14"))).isEqualByComparingTo("200");
        assertThat(FeeRules.feeFor(p, Map.of())).isEqualByComparingTo("200");
        assertThat(FeeRules.feeFor(p, Map.of("age", "Guest"))).isNull();  // free
        assertThat(FeeRules.byAnswer(p)).isTrue();
    }

    @Test
    void no_base_fee_but_priced_options_still_counts_as_having_fees() {
        Program p = camp();
        p.setRegistrationFee(null);
        assertThat(FeeRules.hasAnyFee(p)).isTrue();
        assertThat(FeeRules.feeFor(p, Map.of("age", "14"))).isNull();
        assertThat(FeeRules.feeFor(p, Map.of("age", "Leader"))).isEqualByComparingTo("280");
    }

    @Test
    void rooms_show_after_check_in_or_from_the_date_whichever_first() {
        Program p = new Program();
        p.setTimezone("UTC");
        p.setRoomVisibility(RoomVisibility.CHECKIN_OR_DATE);
        p.setRoomVisibleFrom("2027-07-02T15:00");
        OffsetDateTime before = OffsetDateTime.of(2027, 7, 2, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime after = OffsetDateTime.of(2027, 7, 2, 15, 0, 0, 0, ZoneOffset.UTC);
        assertThat(RoomVisibility.lock(p, null, before)).isNotNull();
        assertThat(RoomVisibility.lock(p, before, before)).isNull();   // checked in
        assertThat(RoomVisibility.lock(p, null, after)).isNull();      // date reached
        p.setRoomVisibility(RoomVisibility.CHECKIN);
        assertThat(RoomVisibility.lock(p, null, after)).containsEntry("reason", "checkin");
        p.setRoomVisibility(null);
        assertThat(RoomVisibility.lock(p, null, before)).isNull();     // always
    }
}
