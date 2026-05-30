package com.btl.transport.unit;

import com.btl.transport.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TemplateRenderingTest {

    private final NotificationService service = new NotificationService(
        mock(com.btl.transport.notification.TwilioService.class),
        mock(com.btl.transport.notification.SendGridService.class),
        mock(com.btl.transport.notification.NotificationConfigRepository.class),
        mock(com.btl.transport.program.ProgramRepository.class)
    );

    @Test
    void renders_single_variable() {
        String result = service.renderTemplate("Hello {{name}}", Map.of("name", "John"));
        assertThat(result).isEqualTo("Hello John");
    }

    @Test
    void renders_multiple_variables() {
        String result = service.renderTemplate(
            "Hi {{name}}, code {{btl_code}}",
            Map.of("name", "Jane", "btl_code", "BTL-001")
        );
        assertThat(result).isEqualTo("Hi Jane, code BTL-001");
    }

    @Test
    void unknown_variable_left_as_is() {
        String result = service.renderTemplate("Hello {{name}} {{unknown}}", Map.of("name", "John"));
        assertThat(result).isEqualTo("Hello John {{unknown}}");
    }

    @Test
    void null_template_returns_empty() {
        assertThat(service.renderTemplate(null, Map.of())).isEmpty();
    }

    @Test
    void registration_template_substitutes_all_vars() {
        String template = "Hi {{name}}, your code is {{btl_code}}. Track: {{link}}";
        String result = service.renderTemplate(template, Map.of(
            "name", "Adeyemi", "btl_code", "BTL-042", "link", "https://btl2026.com/status?code=BTL-042"
        ));
        assertThat(result).doesNotContain("{{");
    }
}
