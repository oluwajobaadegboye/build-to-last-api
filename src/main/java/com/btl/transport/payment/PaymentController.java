package com.btl.transport.payment;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

/** Payment tracking for a participant (permission checks: JwtAuthFilter + AdminPermissions). */
@Tag(name = "Payments", description = "Record payments made outside the app and track paid / part paid / unpaid")
@RestController
@RequestMapping("/api/v1/admin/participants/{code}")
@RequiredArgsConstructor
public class PaymentController {

    private final ParticipantRepository participantRepository;
    private final PaymentService paymentService;
    private final AdminAccess adminAccess;

    public record RecordPaymentRequest(BigDecimal amount, String note, String status) {}
    public record StatusRequest(String status) {}

    private Participant load(String code) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getProgramId());
        return p;
    }

    @Operation(summary = "Payment summary", description = "Status, total paid, fee/balance and every recorded payment")
    @GetMapping("/payments")
    public Map<String, Object> summary(@PathVariable String code) {
        return paymentService.summary(load(code));
    }

    @Operation(summary = "Record a payment", description = "Body: { amount, note?, status? } — status (partial|paid) is used only when the program has no fee")
    @PostMapping("/payments")
    public Map<String, Object> record(@PathVariable String code, @RequestBody RecordPaymentRequest req) {
        return paymentService.record(load(code), req.amount(), req.note(), req.status(), adminAccess.currentUsername());
    }

    @Operation(summary = "Delete a payment", description = "Removes a mistaken entry and updates the status")
    @DeleteMapping("/payments/{paymentId}")
    public Map<String, Object> delete(@PathVariable String code, @PathVariable Integer paymentId) {
        return paymentService.delete(load(code), paymentId);
    }

    @Operation(summary = "Set payment status", description = "Manual override: unpaid | partial | paid (e.g. paid for a waived fee)")
    @PatchMapping("/payment-status")
    public Map<String, Object> setStatus(@PathVariable String code, @RequestBody StatusRequest req) {
        return paymentService.setStatus(load(code), req.status());
    }
}
