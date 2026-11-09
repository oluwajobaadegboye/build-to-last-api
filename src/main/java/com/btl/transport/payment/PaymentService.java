package com.btl.transport.payment;

import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Payment tracking (payments themselves happen outside the app). A participant's status is
 * unpaid / partial / paid. When the program has a registration fee, the status follows the total
 * paid; without a fee it is set by the admin (a payment can be recorded as part or full).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String UNPAID = "unpaid";
    public static final String PARTIAL = "partial";
    public static final String PAID = "paid";
    public static final Set<String> STATUSES = Set.of(UNPAID, PARTIAL, PAID);

    private final ParticipantPaymentRepository paymentRepository;
    private final ParticipantRepository participantRepository;
    private final ProgramRepository programRepository;
    private final com.btl.transport.notification.SendGridService sendGridService;

    public static String normalizeStatus(String s) {
        return s != null && STATUSES.contains(s.trim().toLowerCase(Locale.ROOT)) ? s.trim().toLowerCase(Locale.ROOT) : UNPAID;
    }

    private Program programOf(Participant p) {
        return p.getProgramId() == null ? null : programRepository.findById(p.getProgramId()).orElse(null);
    }

    private static BigDecimal fee(Program prog) {
        BigDecimal f = prog == null ? null : prog.getRegistrationFee();
        return f != null && f.signum() > 0 ? f : null;
    }

    /** Status implied by the total paid against the fee. */
    static String statusFor(BigDecimal total, BigDecimal fee) {
        if (total == null || total.signum() <= 0) return UNPAID;
        return total.compareTo(fee) >= 0 ? PAID : PARTIAL;
    }

    @Transactional
    public Map<String, Object> record(Participant p, BigDecimal amount, String note, String statusIfNoFee, String recordedBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be more than 0");
        }
        if (amount.scale() > 2) amount = amount.setScale(2, RoundingMode.HALF_UP);
        paymentRepository.save(ParticipantPayment.builder()
            .participantId(p.getId()).programId(p.getProgramId())
            .amount(amount)
            .note(note == null || note.isBlank() ? null : note.trim())
            .recordedBy(recordedBy)
            .recordedAt(OffsetDateTime.now())
            .build());
        BigDecimal fee = fee(programOf(p));
        BigDecimal total = paymentRepository.totalForParticipant(p.getId());
        if (fee != null) {
            p.setPaymentStatus(statusFor(total, fee));
        } else if (PAID.equalsIgnoreCase(statusIfNoFee)) {
            p.setPaymentStatus(PAID);
        } else if (!PAID.equals(p.getPaymentStatus())) {
            p.setPaymentStatus(PARTIAL);
        }
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        Map<String, Object> out = summary(p);
        sendReceipt(p, amount, out);
        return out;
    }

    // ── Visibility & attendee emails ───────────────────────────────────────

    /** Full and Payments admins always see amounts; Registrations-only admins only if the program allows it. */
    public static boolean canSeeAmounts(Program prog, Set<String> perms) {
        if (perms.contains(com.btl.transport.admin.AdminPermissions.FULL) || perms.contains(com.btl.transport.admin.AdminPermissions.PAYMENTS)) return true;
        return prog == null || !Boolean.FALSE.equals(prog.getRegistrationsSeeAmounts());
    }

    /** Summary with money removed (status only). */
    public static Map<String, Object> withoutAmounts(Map<String, Object> summary) {
        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("total_paid", null);
        out.put("fee", null);
        out.put("balance", null);
        out.put("payments", List.of());
        out.put("amounts_hidden", true);
        return out;
    }

    /** What an attendee sees when the program shows payment status (no ledger, no admin names). */
    public Map<String, Object> attendeeSummary(Participant p) {
        Map<String, Object> s = summary(p);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : List.of("status", "total_paid", "fee", "currency", "balance")) out.put(k, s.get(k));
        return out;
    }

    private static String money(Object amount, Object currency) {
        BigDecimal n = amount instanceof BigDecimal b ? b : BigDecimal.ZERO;
        return currency + " " + n.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void sendReceipt(Participant p, BigDecimal amount, Map<String, Object> summary) {
        Program prog = programOf(p);
        if (prog == null || !Boolean.TRUE.equals(prog.getPaymentEmailsEnabled()) || p.getEmail() == null || p.getEmail().isBlank()) return;
        var brand = com.btl.transport.notification.ProgramBranding.of(prog);
        Object cur = summary.get("currency");
        StringBuilder body = new StringBuilder("Hi ").append(p.getFullName()).append(",\n\n")
            .append("We've received your payment of ").append(money(amount, cur)).append(" for ").append(brand.nameOrGeneric()).append(".\n\n")
            .append("Total paid: ").append(money(summary.get("total_paid"), cur)).append("\n");
        if (summary.get("fee") != null) body.append("Balance: ").append(money(summary.get("balance"), cur)).append("\n");
        body.append("Status: ").append(label(String.valueOf(summary.get("status")))).append("\n\n")
            .append("Your registration code: ").append(p.getBtlCode()).append("\n\n").append(brand.teamName());
        String html = com.btl.transport.notification.EmailLayout.forProgram(brand)
            .title("Payment received")
            .preheader("We've received " + money(amount, cur) + " for " + brand.nameOrGeneric())
            .greeting(p.getFullName())
            .paragraph("We've received your payment of " + money(amount, cur) + " for " + brand.nameOrGeneric() + ". Thank you.")
            .details("Payment summary", List.of(
                com.btl.transport.notification.EmailLayout.row("This payment", money(amount, cur)),
                com.btl.transport.notification.EmailLayout.row("Total paid", money(summary.get("total_paid"), cur)),
                com.btl.transport.notification.EmailLayout.row("Balance", summary.get("fee") != null ? money(summary.get("balance"), cur) : null),
                com.btl.transport.notification.EmailLayout.row("Status", label(String.valueOf(summary.get("status")))),
                com.btl.transport.notification.EmailLayout.row("Registration code", p.getBtlCode())))
            .footerNote("Keep this email as your receipt.")
            .html();
        sendGridService.sendHtmlEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Payment received"), html, body.toString());
    }

    private static String label(String status) {
        return switch (status) { case PAID -> "Paid in full"; case PARTIAL -> "Part paid"; default -> "Unpaid"; };
    }

    /** Emails a reminder to everyone in the program whose status is in {@code statuses}; returns how many were sent. */
    @Transactional(readOnly = true)
    public int sendReminders(String programId, Collection<String> statuses) {
        Program prog = programRepository.findById(programId).orElse(null);
        if (prog == null || !Boolean.TRUE.equals(prog.getPaymentEmailsEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Turn on payment emails for this program first");
        }
        Set<String> wanted = new HashSet<>();
        for (String s : statuses == null || statuses.isEmpty() ? List.of(UNPAID, PARTIAL) : statuses) wanted.add(normalizeStatus(s));
        wanted.remove(PAID);
        var brand = com.btl.transport.notification.ProgramBranding.of(prog);
        int sent = 0;
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(programId)) {
            if (!wanted.contains(normalizeStatus(p.getPaymentStatus())) || p.getEmail() == null || p.getEmail().isBlank()) continue;
            Map<String, Object> s = summary(p);
            Object cur = s.get("currency");
            StringBuilder body = new StringBuilder("Hi ").append(p.getFullName()).append(",\n\n")
                .append("This is a friendly reminder about your payment for ").append(brand.nameOrGeneric()).append(".\n\n")
                .append("Paid so far: ").append(money(s.get("total_paid"), cur)).append("\n");
            if (s.get("fee") != null) body.append("Balance due: ").append(money(s.get("balance"), cur)).append("\n");
            body.append("\nIf you've already paid, please ignore this message.\n\n").append(brand.teamName());
            String html = com.btl.transport.notification.EmailLayout.forProgram(brand)
                .title("Payment reminder")
                .preheader(s.get("fee") != null ? "Balance due: " + money(s.get("balance"), cur) : "A reminder about your payment")
                .greeting(p.getFullName())
                .paragraph("This is a friendly reminder about your payment for " + brand.nameOrGeneric() + ".")
                .details(null, List.of(
                    com.btl.transport.notification.EmailLayout.row("Paid so far", money(s.get("total_paid"), cur)),
                    com.btl.transport.notification.EmailLayout.row("Balance due", s.get("fee") != null ? money(s.get("balance"), cur) : null),
                    com.btl.transport.notification.EmailLayout.row("Registration code", p.getBtlCode())))
                .note("If you've already paid, please ignore this message.")
                .html();
            sendGridService.sendHtmlEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Payment reminder"), html, body.toString());
            sent++;
        }
        return sent;
    }

    @Transactional
    public Map<String, Object> delete(Participant p, Integer paymentId) {
        ParticipantPayment pay = paymentRepository.findById(paymentId)
            .filter(x -> x.getParticipantId().equals(p.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        paymentRepository.delete(pay);
        paymentRepository.flush();
        BigDecimal fee = fee(programOf(p));
        BigDecimal total = paymentRepository.totalForParticipant(p.getId());
        if (fee != null) p.setPaymentStatus(statusFor(total, fee));
        else if (total.signum() <= 0) p.setPaymentStatus(UNPAID);
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return summary(p);
    }

    /** Manual override, e.g. "paid" for a waived fee. */
    @Transactional
    public Map<String, Object> setStatus(Participant p, String status) {
        if (status == null || !STATUSES.contains(status.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be unpaid, partial or paid");
        }
        p.setPaymentStatus(status.trim().toLowerCase(Locale.ROOT));
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return summary(p);
    }

    public Map<String, Object> summary(Participant p) {
        Program prog = programOf(p);
        BigDecimal fee = fee(prog);
        BigDecimal total = paymentRepository.totalForParticipant(p.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", normalizeStatus(p.getPaymentStatus()));
        out.put("total_paid", total);
        out.put("fee", fee);
        out.put("currency", prog != null && prog.getFeeCurrency() != null ? prog.getFeeCurrency() : "USD");
        out.put("balance", fee != null ? fee.subtract(total).max(BigDecimal.ZERO) : null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParticipantPayment pay : paymentRepository.findByParticipantIdOrderByRecordedAtAsc(p.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pay.getId());
            m.put("amount", pay.getAmount());
            m.put("note", pay.getNote());
            m.put("recorded_by", pay.getRecordedBy());
            m.put("recorded_at", pay.getRecordedAt() != null ? pay.getRecordedAt().toString() : null);
            rows.add(m);
        }
        out.put("payments", rows);
        return out;
    }

    /** Totals for a set of participants (for list views and exports). */
    public Map<Integer, BigDecimal> totals(Collection<Integer> participantIds) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        if (participantIds == null || participantIds.isEmpty()) return out;
        for (Object[] row : paymentRepository.totalsFor(participantIds)) {
            out.put((Integer) row[0], (BigDecimal) row[1]);
        }
        return out;
    }
}
