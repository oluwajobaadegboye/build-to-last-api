package org.programhub.unit;

import org.programhub.infrastructure.GoogleSheetsClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSheetsClientTest {

    @Test
    void reads_the_sheet_id_from_any_sheet_link() {
        String id = "1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-xyz";
        assertThat(GoogleSheetsClient.sheetId("https://docs.google.com/spreadsheets/d/" + id + "/edit#gid=0")).isEqualTo(id);
        assertThat(GoogleSheetsClient.sheetId("https://docs.google.com/spreadsheets/u/1/d/" + id + "/edit?usp=sharing")).isEqualTo(id);
        assertThat(GoogleSheetsClient.sheetId(id)).isEqualTo(id);
        assertThat(GoogleSheetsClient.sheetId("https://docs.google.com/document/d/short/edit")).isNull();
        assertThat(GoogleSheetsClient.sheetId("not a link")).isNull();
    }

    @Test
    void without_a_key_it_is_simply_off() {
        GoogleSheetsClient c = new GoogleSheetsClient("");
        assertThat(c.isConfigured()).isFalse();
        assertThat(new GoogleSheetsClient("{not json").isConfigured()).isFalse();
        assertThat(new GoogleSheetsClient("PLACEHOLDER").isConfigured()).isFalse();  // Terraform's stand-in value
    }
}
