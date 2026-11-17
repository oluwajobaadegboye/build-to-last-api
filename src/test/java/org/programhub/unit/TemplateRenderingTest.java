package org.programhub.unit;

import org.programhub.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.programhub.notification.NotificationConfigRepository;
import org.programhub.notification.PaymentInstructions;
import org.programhub.notification.SendGridService;
import org.programhub.notification.TwilioService;
import org.programhub.program.ProgramPaymentQrRepository;
import org.programhub.program.ProgramRepository;

class TemplateRenderingTest {

    private final NotificationService service = new NotificationService(
        mock(TwilioService.class),
        mock(SendGridService.class),
        mock(NotificationConfigRepository.class),
        mock(ProgramRepository.class),
        new PaymentInstructions(mock(ProgramPaymentQrRepository.class))
    );

    @Test
    void renders_single_variable() {
        String result = service.renderTemplate("Hello {{name}}", Map.of("name", "John"));
        assertThat(result).isEqualTo("Hello John");
    }

    @Test
    void renders_multiple_variables() {
        String result = service.renderTemplate(
            "Hi {{name}}, code {{reg_code}}",
            Map.of("name", "Jane", "reg_code", "BTL-001")
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
        String template = "Hi {{name}}, your code is {{reg_code}}. Track: {{link}}";
        String result = service.renderTemplate(template, Map.of(
            "name", "Adeyemi", "reg_code", "BTL-042", "link", "https://btl2026.com/status?code=BTL-042"
        ));
        assertThat(result).doesNotContain("{{");
    }
}
