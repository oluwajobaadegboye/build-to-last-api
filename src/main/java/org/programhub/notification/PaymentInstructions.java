package org.programhub.notification;

import org.programhub.program.Program;
import org.programhub.program.ProgramPaymentQrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/** The "How to pay" part of emails: amount due, the program's instructions and its QR code (attached inline). */
@Component
@RequiredArgsConstructor
public class PaymentInstructions {

    static final String QR_CID = "payment-qr";

    private final ProgramPaymentQrRepository qrRepository;

    /**
     * Adds the section when there's something to say (an amount due, instructions or a QR code).
     * Returns the inline images to send with the email.
     */
    public List<SendGridService.InlineImage> addTo(EmailLayout email, Program prog, BigDecimal amountDue) {
        List<SendGridService.InlineImage> images = new ArrayList<>();
        if (prog == null) return images;
        String text = prog.getPaymentInstructions() != null ? prog.getPaymentInstructions().trim() : "";
        var qr = qrRepository.findById(prog.getId()).orElse(null);
        boolean due = amountDue != null && amountDue.signum() > 0;
        if (!due && text.isEmpty() && qr == null) return images;
        email.heading("How to pay");
        if (due) email.details(null, Collections.singletonList(EmailLayout.row("Amount to pay", money(amountDue, prog.getFeeCurrency()))));
        email.text(text, "this link");
        if (qr != null) {
            email.image("cid:" + QR_CID, "Payment QR code", 200);
            images.add(new SendGridService.InlineImage(QR_CID, qr.getContentType(), qr.getData()));
        }
        return images;
    }

    /** Plain-text version for the text part of the email. */
    public String plain(Program prog, BigDecimal amountDue) {
        if (prog == null) return "";
        StringBuilder sb = new StringBuilder();
        if (amountDue != null && amountDue.signum() > 0) sb.append("Amount to pay: ").append(money(amountDue, prog.getFeeCurrency())).append("\n");
        if (prog.getPaymentInstructions() != null && !prog.getPaymentInstructions().isBlank()) sb.append(prog.getPaymentInstructions().trim()).append("\n");
        return sb.length() == 0 ? "" : "\nHow to pay\n" + sb;
    }

    static String money(BigDecimal amount, String currency) {
        String cur = currency != null && !currency.isBlank() ? currency : "USD";
        String sym = switch (cur) { case "USD", "CAD", "AUD" -> "$"; case "GBP" -> "£"; case "EUR" -> "€"; case "NGN" -> "₦"; default -> ""; };
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        String n = v.stripTrailingZeros().scale() <= 0 ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : v.toPlainString();
        return sym.isEmpty() ? cur + " " + n : sym + n + (cur.equals("USD") ? "" : " " + cur);
    }
}
