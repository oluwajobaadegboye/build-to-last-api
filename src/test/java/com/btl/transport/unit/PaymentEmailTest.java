package com.btl.transport.unit;

import com.btl.transport.notification.*;
import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramPaymentQr;
import com.btl.transport.program.ProgramPaymentQrRepository;
import com.btl.transport.program.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The registration email carries the person's own fee (by their answer), the instructions and the QR code. */
class PaymentEmailTest {

    @Test
    @SuppressWarnings("unchecked")
    void registration_email_has_their_fee_instructions_and_qr() throws Exception {
        SendGridService sendGrid = mock(SendGridService.class);
        ProgramRepository programs = mock(ProgramRepository.class);
        NotificationConfigRepository configs = mock(NotificationConfigRepository.class);
        ProgramPaymentQrRepository qrs = mock(ProgramPaymentQrRepository.class);
        NotificationService service = new NotificationService(mock(TwilioService.class), sendGrid, configs, programs, new PaymentInstructions(qrs));
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example.com");
        ReflectionTestUtils.setField(service, "smsEnabled", false);

        Program camp = Program.builder().id("camp").name("Youth Camp").startDate("Jul 2, 2027").endDate("Jul 5, 2027")
            .registrationFee(new BigDecimal("200")).feeCurrency("USD")
            .feeQuestionKey("age").feeByOption("{\"Leader\":280}")
            .paymentInstructions("Pay by Zelle to payments@camp.org (Camp Inc).\nPut your name and code in the memo.").build();
        when(programs.findById("camp")).thenReturn(Optional.of(camp));
        when(qrs.findById("camp")).thenReturn(Optional.of(ProgramPaymentQr.builder().programId("camp").contentType("image/png").data(new byte[]{1, 2, 3}).build()));

        service.sendRegistrationConfirmation(Participant.builder().programId("camp").fullName("Lee Leader").email("lee@example.com")
            .regCode("YC-001").customAnswers("{\"age\":\"Leader\"}").build());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SendGridService.InlineImage>> images = ArgumentCaptor.forClass(List.class);
        verify(sendGrid).sendHtmlEmailAs(anyString(), anyString(), anyString(), anyString(), html.capture(), plain.capture(), images.capture());
        assertThat(html.getValue()).contains("How to pay").contains("Amount to pay").contains("$280").contains("payments@camp.org").contains("cid:payment-qr");
        assertThat(plain.getValue()).contains("Amount to pay: $280");
        assertThat(images.getValue()).hasSize(1);
        Path dir = Path.of("target", "email-previews");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("registration-with-payment.html"), html.getValue());
    }
}
