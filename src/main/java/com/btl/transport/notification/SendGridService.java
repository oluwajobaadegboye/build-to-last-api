package com.btl.transport.notification;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.ClickTrackingSetting;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.sendgrid.helpers.mail.objects.TrackingSettings;
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
        sendEmailAs(null, toEmail, toName, subject, body);
    }

    public void sendHtmlEmail(String toEmail, String toName, String subject,
                              String htmlBody, String plainBody) {
        sendHtmlEmailAs(null, toEmail, toName, subject, htmlBody, plainBody);
    }

    /** Sends as {@code senderName} (e.g. the program's name); null/blank uses the configured default. */
    public void sendEmailAs(String senderName, String toEmail, String toName, String subject, String body) {
        try {
            Mail mail = new Mail(new Email(fromEmail, sender(senderName)), subject,
                new Email(toEmail, toName), new Content("text/plain", body));
            send(mail, toEmail, subject);
        } catch (Exception e) {
            log.error("SendGrid send failed — to={} subject={}", toEmail, subject, e);
        }
    }

    /** Sends as {@code senderName} (e.g. the program's name); null/blank uses the configured default. */
    public void sendHtmlEmailAs(String senderName, String toEmail, String toName, String subject,
                                String htmlBody, String plainBody) {
        try {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, sender(senderName)));
            mail.setSubject(subject);
            Personalization personalization = new Personalization();
            personalization.addTo(new Email(toEmail, toName));
            mail.addPersonalization(personalization);
            mail.addContent(new Content("text/plain", plainBody));
            mail.addContent(new Content("text/html", htmlBody));
            send(mail, toEmail, subject);
        } catch (Exception e) {
            log.error("SendGrid send failed — to={} subject={}", toEmail, subject, e);
        }
    }

    private String sender(String senderName) {
        return senderName != null && !senderName.isBlank() ? senderName.trim() : fromName;
    }

    private void send(Mail mail, String toEmail, String subject) throws Exception {
        // No click tracking: SendGrid would rewrite every link into a long tracking URL, which looks
        // untrustworthy and routes one-time links (email confirmation, driver access) through a third party.
        TrackingSettings tracking = new TrackingSettings();
        ClickTrackingSetting click = new ClickTrackingSetting();
        click.setEnable(false);
        click.setEnableText(false);
        tracking.setClickTrackingSetting(click);
        mail.setTrackingSettings(tracking);
        log.info("Sending email — to={} subject={}", toEmail, subject);
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);
        if (response.getStatusCode() >= 400) {
            log.error("SendGrid rejected email — to={} subject={} status={} body={}",
                toEmail, subject, response.getStatusCode(), response.getBody());
        } else {
            log.info("Email delivered — to={} subject={} status={}", toEmail, subject, response.getStatusCode());
        }
    }
}
