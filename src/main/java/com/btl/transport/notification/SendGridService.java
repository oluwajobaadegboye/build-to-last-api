package com.btl.transport.notification;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SendGridService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    public void sendEmail(String toEmail, String toName, String subject, String body) {
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail, toName);
            Content content = new Content("text/plain", body);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid error {}: {}", response.getStatusCode(), response.getBody());
            } else {
                log.info("Email sent to {}", toEmail);
            }
        } catch (Exception e) {
            log.error("SendGrid send failed to {}: {}", toEmail, e.getMessage());
            // Best-effort — SMS is the primary channel
        }
    }
}
