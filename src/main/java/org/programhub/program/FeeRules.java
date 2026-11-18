package org.programhub.program;

import org.programhub.participant.Participant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who pays what. A program has a base fee and, optionally, "fee by answer": one question whose options
 * each set a fee (e.g. "How old are you?" → 12–18 pay 200, Leader pays 280). Options without an amount,
 * and people who haven't answered, pay the base fee. A fee of 0 or none means nothing to pay.
 */
public final class FeeRules {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FeeRules() {}

    /** {option: amount} for the fee question (only positive or zero amounts); empty when not used. */
    public static Map<String, BigDecimal> optionFees(Program prog) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (prog == null || prog.getFeeQuestionKey() == null || prog.getFeeQuestionKey().isBlank()
            || prog.getFeeByOption() == null || prog.getFeeByOption().isBlank()) return out;
        try {
            Map<String, Object> raw = JSON.readValue(prog.getFeeByOption(), new TypeReference<Map<String, Object>>() {});
            for (var e : raw.entrySet()) {
                if (e.getValue() == null || String.valueOf(e.getValue()).isBlank()) continue;
                try {
                    BigDecimal v = new BigDecimal(String.valueOf(e.getValue()));
                    if (v.signum() >= 0) out.put(e.getKey(), v);
                } catch (NumberFormatException ignored) { /* skip bad values */ }
            }
        } catch (Exception ignored) { /* unreadable: treat as not used */ }
        return out;
    }

    /** Whether fees differ by answer (at least one option has its own amount). */
    public static boolean byAnswer(Program prog) {
        return !optionFees(prog).isEmpty();
    }

    private static BigDecimal positive(BigDecimal v) {
        return v != null && v.signum() > 0 ? v : null;
    }

    /** The fee for someone with these answers; null when there's nothing to pay. */
    public static BigDecimal feeFor(Program prog, Map<String, Object> answers) {
        if (prog == null) return null;
        Map<String, BigDecimal> byOption = optionFees(prog);
        if (!byOption.isEmpty() && answers != null) {
            Object a = answers.get(prog.getFeeQuestionKey());
            if (a != null && byOption.containsKey(String.valueOf(a))) return positive(byOption.get(String.valueOf(a)));
        }
        return positive(prog.getRegistrationFee());
    }

    /** The fee for this participant (from their saved answers). */
    public static BigDecimal feeFor(Program prog, Participant p) {
        return feeFor(prog, answers(p == null ? null : p.getCustomAnswers()));
    }

    /** Whether anyone in the program has something to pay (base fee or any priced option). */
    public static boolean hasAnyFee(Program prog) {
        if (prog == null) return false;
        if (positive(prog.getRegistrationFee()) != null) return true;
        return optionFees(prog).values().stream().anyMatch(v -> v.signum() > 0);
    }

    public static Map<String, Object> answers(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return JSON.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    public static String toJson(Map<String, BigDecimal> byOption) {
        try { return byOption == null || byOption.isEmpty() ? null : JSON.writeValueAsString(byOption); }
        catch (Exception e) { return null; }
    }
}
