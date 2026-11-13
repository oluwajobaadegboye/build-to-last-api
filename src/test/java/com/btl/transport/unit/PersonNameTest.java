package com.btl.transport.unit;

import com.btl.transport.participant.PersonName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PersonNameTest {

    @Test
    void full_name_is_first_then_last_with_tidy_spaces() {
        assertThat(PersonName.join("  Ada ", " Lovelace ")).isEqualTo("Ada Lovelace");
        assertThat(PersonName.join("Mary  Ann", "Smith")).isEqualTo("Mary Ann Smith");
        assertThat(PersonName.join("Ada", " ")).isEqualTo("Ada");
        assertThat(PersonName.clean("   ")).isNull();
    }

    @Test
    void gender_accepts_male_or_female_only() {
        assertThat(PersonName.gender("Male")).isEqualTo("male");
        assertThat(PersonName.gender(" FEMALE ")).isEqualTo("female");
        assertThat(PersonName.gender("")).isNull();
        assertThat(PersonName.gender(null)).isNull();
        assertThatThrownBy(() -> PersonName.gender("other")).isInstanceOf(IllegalArgumentException.class);
    }
}
