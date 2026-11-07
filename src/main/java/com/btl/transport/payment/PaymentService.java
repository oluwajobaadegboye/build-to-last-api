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
        return summary(p);
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
