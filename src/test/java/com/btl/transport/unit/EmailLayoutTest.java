package com.btl.transport.unit;

import com.btl.transport.notification.EmailLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    @Test
    void escapes_everything_it_is_given() {
        String html = EmailLayout.forPlatform().title("<b>Hi</b>").greeting("Casey <script>")
            .details(null, List.<String[]>of(EmailLayout.row("Name", "A & B"))).html();
        assertThat(html).contains("&lt;b&gt;Hi&lt;/b&gt;", "Casey &lt;script&gt;", "A &amp; B").doesNotContain("<script>");
    }

    @Test
    void urls_in_free_text_become_short_links() {
        String html = EmailLayout.forPlatform()
            .text("Check https://app.example.com/status?code=X1&a=b for details.", "your registration page").html();
        assertThat(html).contains("href=\"https://app.example.com/status?code=X1&amp;a=b\"", ">your registration page</a>")
            .doesNotContain(">https://app.example.com/status");
    }

    @Test
    void button_writes_the_link_out_as_a_fallback_and_rows_without_values_are_dropped() {
        String html = EmailLayout.forPlatform().button("Confirm email", "https://app.example.com/verify?token=abc")
            .details("Summary", List.of(EmailLayout.row("Balance", null), EmailLayout.row("Paid", "USD 5.00"))).html();
        assertThat(html).contains(">Confirm email</a>", "Button not working?", "USD 5.00").doesNotContain("Balance");
    }
}
