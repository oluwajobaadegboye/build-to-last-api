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
    private final com.btl.transport.notification.PaymentInstructions paymentInstructions;

    public static String normalizeStatus(String s) {
        return s != null && STATUSES.contains(s.trim().toLowerCase(Locale.ROOT)) ? s.trim().toLowerCase(Locale.ROOT) : UNPAID;
    }

    private Program programOf(Participant p) {
        return p.getProgramId() == null ? null : programRepository.findById(p.getProgramId()).orElse(null);
    }

    /** This person's fee (the base fee, or their answer's fee when fees differ by answer); null = nothing to pay. */
    private static BigDecimal fee(Program prog, Participant p) {
        return com.btl.transport.program.FeeRules.feeFor(prog, p);
    }

    /** Total paid so far (payments less refunds). */
    public BigDecimal totalPaid(Participant p) {
        return paymentRepository.totalForParticipant(p.getId());
    }

    /** This person's fee, for emails and screens; null = nothing to pay. */
    public BigDecimal feeFor(Participant p) {
        return fee(programOf(p), p);
    }

    /** Status implied by the total paid against the fee. */
    static String statusFor(BigDecimal total, BigDecimal fee) {
        if (total == null || total.signum() <= 0) return UNPAID;
        return total.compareTo(fee) >= 0 ? PAID : PARTIAL;
    }

    public static final String REFUND = "refund";

    private static String method(String m) {
        if (m == null || m.isBlank()) return null;
        String t = m.trim();
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    public Map<String, Object> record(Participant p, BigDecimal amount, String note, String statusIfNoFee, String recordedBy) {
        return record(p, amount, note, statusIfNoFee, recordedBy, null);
    }

    /**
     * A refund given back to the attendee (e.g. sent back on Zelle). Stored as its own ledger line and
     * subtracted from the total paid; it can't be more than what they've paid.
     */
    @Transactional
    public Map<String, Object> refund(Participant p, BigDecimal amount, String note, String method, String recordedBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund must be more than 0");
        }
        if (amount.scale() > 2) amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = paymentRepository.totalForParticipant(p.getId());
        if (amount.compareTo(paid) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A refund can't be more than they've paid (" + paid.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
        }
        paymentRepository.save(ParticipantPayment.builder()
            .participantId(p.getId()).programId(p.getProgramId())
            .amount(amount).kind(REFUND).method(method(method))
            .note(note == null || note.isBlank() ? null : note.trim())
            .recordedBy(recordedBy)
            .recordedAt(OffsetDateTime.now())
            .build());
        paymentRepository.flush();
        restatus(p);
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return summary(p);
    }

    /** Status after the ledger (or their fee) changed: follows the fee when there is one; without a fee, nothing paid = unpaid. */
    private void restatus(Participant p) {
        BigDecimal fee = fee(programOf(p), p);
        BigDecimal total = paymentRepository.totalForParticipant(p.getId());
        if (fee != null) p.setPaymentStatus(statusFor(total, fee));
        else if (total.signum() <= 0) p.setPaymentStatus(UNPAID);
    }

    /**
     * After an answer that sets the fee changes (admin edit or self-edit): re-check this person's status.
     * Nothing recorded keeps the status, so a manual "paid" (a waived fee) isn't undone.
     */
    @Transactional
    public void recheck(Participant p) {
        BigDecimal total = paymentRepository.totalForParticipant(p.getId());
        BigDecimal fee = fee(programOf(p), p);
        if (fee == null || total.signum() <= 0) return;
        String next = statusFor(total, fee);
        if (!next.equals(normalizeStatus(p.getPaymentStatus()))) {
            p.setPaymentStatus(next);
            participantRepository.save(p);
        }
    }

    /**
     * After the program's fee changes: re-check everyone who has paid something against the new fee.
     * People with nothing recorded keep their status, so a manual "paid" (a waived fee) isn't undone.
     * Returns how many statuses changed.
     */
    @Transactional
    public int recalculateForProgram(String programId) {
        Program prog = programRepository.findById(programId).orElse(null);
        if (!com.btl.transport.program.FeeRules.hasAnyFee(prog)) return 0;
        List<Participant> people = participantRepository.findAllByProgramIdWithHotel(programId);
        Map<Integer, BigDecimal> totals = totals(people.stream().map(Participant::getId).toList());
        int changed = 0;
        for (Participant p : people) {
            BigDecimal total = totals.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal fee = fee(prog, p);
            if (total.signum() <= 0 || fee == null) continue;
            String next = statusFor(total, fee);
            if (!next.equals(normalizeStatus(p.getPaymentStatus()))) {
                p.setPaymentStatus(next);
                p.setUpdatedAt(OffsetDateTime.now());
                participantRepository.save(p);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public Map<String, Object> record(Participant p, BigDecimal amount, String note, String statusIfNoFee, String recordedBy, String method) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be more than 0");
        }
        if (amount.scale() > 2) amount = amount.setScale(2, RoundingMode.HALF_UP);
        paymentRepository.save(ParticipantPayment.builder()
            .participantId(p.getId()).programId(p.getProgramId())
            .amount(amount).method(method(method))
            .note(note == null || note.isBlank() ? null : note.trim())
            .recordedBy(recordedBy)
            .recordedAt(OffsetDateTime.now())
            .build());
        BigDecimal fee = fee(programOf(p), p);
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
        out.put("total_refunded", null);
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
            .append("Your registration code: ").append(p.getRegCode()).append("\n\n").append(brand.teamName());
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
                com.btl.transport.notification.EmailLayout.row("Registration code", p.getRegCode())))
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
        if (prog == null || !Boolean.TRUE.equals(prog.getPaymentRemindersEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Turn on payment reminders for this program first (Settings → General)");
        }
        Set<String> wanted = new HashSet<>();
        for (String s : statuses == null || statuses.isEmpty() ? List.of(UNPAID, PARTIAL) : statuses) wanted.add(normalizeStatus(s));
        wanted.remove(PAID);
        var brand = com.btl.transport.notification.ProgramBranding.of(prog);
        int sent = 0;
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(programId)) {
            if (!wanted.contains(normalizeStatus(p.getPaymentStatus())) || p.getEmail() == null || p.getEmail().isBlank()) continue;
            if (p.getWaitlistedAt() != null) continue;  // no place yet: nothing to pay
            Map<String, Object> s = summary(p);
            Object cur = s.get("currency");
            StringBuilder body = new StringBuilder("Hi ").append(p.getFullName()).append(",\n\n")
                .append("This is a friendly reminder about your payment for ").append(brand.nameOrGeneric()).append(".\n\n")
                .append("Paid so far: ").append(money(s.get("total_paid"), cur)).append("\n");
            if (s.get("fee") != null) body.append("Balance due: ").append(money(s.get("balance"), cur)).append("\n");
            body.append("\nIf you've already paid, please ignore this message.\n\n").append(brand.teamName());
            var email = com.btl.transport.notification.EmailLayout.forProgram(brand)
                .title("Payment reminder")
                .preheader(s.get("fee") != null ? "Balance due: " + money(s.get("balance"), cur) : "A reminder about your payment")
                .greeting(p.getFullName())
                .paragraph("This is a friendly reminder about your payment for " + brand.nameOrGeneric() + ".")
                .details(null, List.of(
                    com.btl.transport.notification.EmailLayout.row("Paid so far", money(s.get("total_paid"), cur)),
                    com.btl.transport.notification.EmailLayout.row("Balance due", s.get("fee") != null ? money(s.get("balance"), cur) : null),
                    com.btl.transport.notification.EmailLayout.row("Registration code", p.getRegCode())));
            // How to pay (instructions and QR); the balance is already shown above.
            var images = paymentInstructions.addTo(email, prog, null);
            String html = email.note("If you've already paid, please ignore this message.").html();
            sendGridService.sendHtmlEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Payment reminder"), html,
                body + paymentInstructions.plain(prog, null), images);
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
        restatus(p);
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
        BigDecimal fee = fee(prog, p);
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
            m.put("kind", REFUND.equals(pay.getKind()) ? REFUND : "payment");
            m.put("method", pay.getMethod());
            m.put("note", pay.getNote());
            m.put("recorded_by", pay.getRecordedBy());
            m.put("recorded_at", pay.getRecordedAt() != null ? pay.getRecordedAt().toString() : null);
            rows.add(m);
        }
        out.put("payments", rows);
        out.put("total_refunded", rows.stream().filter(r -> REFUND.equals(r.get("kind")))
            .map(r -> (BigDecimal) r.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add));
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
