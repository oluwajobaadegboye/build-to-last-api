package org.programhub.payment;

import org.programhub.admin.Audit;

import org.programhub.admin.AdminAccess;
import org.programhub.participant.Participant;
import org.programhub.participant.ParticipantRepository;
import org.programhub.program.Program;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Locale;
import org.programhub.program.ProgramRepository;

/** Payment tracking for a participant (permission checks: JwtAuthFilter + AdminPermissions). */
@Tag(name = "Payments", description = "Record payments made outside the app and track paid / part paid / unpaid")
@RestController
@RequestMapping("/api/v1/admin/participants/{code}")
@RequiredArgsConstructor
public class PaymentController {

    private final ParticipantRepository participantRepository;
    private final PaymentService paymentService;
    private final AdminAccess adminAccess;
    private final ProgramRepository programRepository;

    public record RecordPaymentRequest(BigDecimal amount, String note, String status, String method) {}
    public record RefundRequest(BigDecimal amount, String note, String method) {}
    public record StatusRequest(String status) {}

    private Participant load(String code) {
        Participant p = participantRepository.findByRegCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getProgramId());
        return p;
    }

    @Operation(summary = "Payment summary", description = "Status, total paid, fee/balance and every recorded payment")
    @GetMapping("/payments")
    public Map<String, Object> summary(@PathVariable String code) {
        Participant p = load(code);
        Map<String, Object> s = paymentService.summary(p);
        Program prog = programRepository.findById(p.getProgramId()).orElse(null);
        return PaymentService.canSeeAmounts(prog, adminAccess.permissions()) ? s : PaymentService.withoutAmounts(s);
    }

    @Operation(summary = "Record a payment", description = "Body: { amount, note?, status? } — status (partial|paid) is used only when the program has no fee")
    @PostMapping("/payments")
    public Map<String, Object> record(@PathVariable String code, @RequestBody RecordPaymentRequest req) {
        Participant p = load(code);
        Map<String, Object> out = paymentService.record(p, req.amount(), req.note(), req.status(), adminAccess.currentUsername(), req.method());
        if (req.amount() != null) {
            String currency = programRepository.findById(p.getProgramId()).map(Program::getFeeCurrency).orElse("");
            Audit.detail((req.amount().stripTrailingZeros().toPlainString() + " " + currency).trim()
                + (req.method() != null && !req.method().isBlank() ? " · " + req.method().trim() : "")
                + (req.note() != null && !req.note().isBlank() ? " · " + req.note().trim() : ""));
        }
        return out;
    }

    @Operation(summary = "Record a refund", description = "Body: { amount, note?, method? } — money given back (e.g. on Zelle); subtracted from the total paid")
    @PostMapping("/refunds")
    public Map<String, Object> refund(@PathVariable String code, @RequestBody RefundRequest req) {
        Participant p = load(code);
        Map<String, Object> out = paymentService.refund(p, req.amount(), req.note(), req.method(), adminAccess.currentUsername());
        String currency = programRepository.findById(p.getProgramId()).map(Program::getFeeCurrency).orElse("");
        Audit.detail(("Refund " + req.amount().stripTrailingZeros().toPlainString() + " " + currency).trim()
            + (req.method() != null && !req.method().isBlank() ? " · " + req.method().trim() : "")
            + (req.note() != null && !req.note().isBlank() ? " · " + req.note().trim() : ""));
        return out;
    }

    @Operation(summary = "Delete a payment", description = "Removes a mistaken entry and updates the status")
    @DeleteMapping("/payments/{paymentId}")
    public Map<String, Object> delete(@PathVariable String code, @PathVariable Integer paymentId) {
        return paymentService.delete(load(code), paymentId);
    }

    @Operation(summary = "Set payment status", description = "Manual override: unpaid | partial | paid (e.g. paid for a waived fee)")
    @PatchMapping("/payment-status")
    public Map<String, Object> setStatus(@PathVariable String code, @RequestBody StatusRequest req) {
        Map<String, Object> out = paymentService.setStatus(load(code), req.status());
        if (req.status() != null) Audit.detail(switch (req.status().toLowerCase(Locale.ROOT)) {
            case "paid" -> "Marked as paid"; case "partial" -> "Marked as part paid"; case "unpaid" -> "Marked as not paid";
            default -> null; });
        return out;
    }
}
