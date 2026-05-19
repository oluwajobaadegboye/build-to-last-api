package com.btl.transport.notification;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
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
            Mail mail = new Mail(new Email(fromEmail, fromName), subject,
                new Email(toEmail, toName), new Content("text/plain", body));
            send(mail, toEmail);
        } catch (Exception e) {
            log.error("SendGrid send failed to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendHtmlEmail(String toEmail, String toName, String subject,
                              String htmlBody, String plainBody) {
        try {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));
            mail.setSubject(subject);
            Personalization personalization = new Personalization();
            personalization.addTo(new Email(toEmail, toName));
            mail.addPersonalization(personalization);
            mail.addContent(new Content("text/plain", plainBody));
            mail.addContent(new Content("text/html", htmlBody));
            send(mail, toEmail);
        } catch (Exception e) {
            log.error("SendGrid send failed to {}: {}", toEmail, e.getMessage());
        }
    }

    private void send(Mail mail, String toEmail) throws Exception {
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
    }
}
