package com.btl.transport.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TwilioService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    @Value("${twilio.whatsapp-from}")
    private String whatsappFrom;

    public void sendSms(String to, String body) {
        try {
            Twilio.init(accountSid, authToken);
            Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), body).create();
            log.info("SMS sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", to, e.getMessage());
            // Best-effort — do not propagate
        }
    }

    public void sendWhatsApp(String to, String body) {
        try {
            Twilio.init(accountSid, authToken);
            Message.creator(
                new PhoneNumber("whatsapp:" + to),
                new PhoneNumber(whatsappFrom),
                body
            ).create();
            log.info("WhatsApp sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp to {}: {}", to, e.getMessage());
        }
    }
}
