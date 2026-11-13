package com.btl.transport.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Definitions, serialization and validation of custom registration questions. */
@Service
@RequiredArgsConstructor
public class RegistrationFieldService {

    public static final Set<String> TYPES =
        Set.of("text", "textarea", "select", "radio", "multiselect", "checkbox", "number", "date", "phone", "email");

    /** Types whose answers come from the question's option list. "radio" is pick-one shown as buttons. */
    public static boolean isChoice(String type) {
        return "select".equals(type) || "radio".equals(type) || "multiselect".equals(type);
    }
    private static final int MAX_TEXT = 2000;
    /** Same rule as the registration phone number: E.164, e.g. +16418191032. */
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final RegistrationFieldRepository repository;
    private final ObjectMapper objectMapper;

    /** Thrown when answers don't satisfy the program's questions; carries per-field messages. */
    public static class InvalidAnswersException extends RuntimeException {
        private final Map<String, String> errors;
        public InvalidAnswersException(Map<String, String> errors) {
            super("Invalid answers: " + errors);
            this.errors = errors;
        }
        public Map<String, String> getErrors() { return errors; }
    }

    public List<String> options(RegistrationField f) {
        if (f.getOptions() == null || f.getOptions().isBlank()) return List.of();
        try { return objectMapper.readValue(f.getOptions(), new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    public String optionsJson(List<String> options) {
        if (options == null) return null;
        List<String> clean = options.stream().filter(Objects::nonNull).map(String::trim)
            .filter(s -> !s.isEmpty()).distinct().toList();
        try { return objectMapper.writeValueAsString(clean); } catch (Exception e) { return "[]"; }
    }

    /** API shape of a question (snake_case keys). */
    public Map<String, Object> toDto(RegistrationField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("key", f.getFieldKey());
        m.put("label", f.getLabel());
        m.put("type", f.getType());
        m.put("options", options(f));
        m.put("required", Boolean.TRUE.equals(f.getRequired()));
        m.put("help_text", f.getHelpText());
        m.put("show_if", showIfDto(f));
        m.put("sort_order", f.getSortOrder());
        m.put("active", Boolean.TRUE.equals(f.getActive()));
        return m;
    }

    /** The answers that make a follow-up question appear (empty when it has no condition). */
    public List<String> showIfValues(RegistrationField f) {
        if (f.getShowIfValues() == null || f.getShowIfValues().isBlank()) return List.of();
        try { return objectMapper.readValue(f.getShowIfValues(), new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> showIfDto(RegistrationField f) {
        if (f.getShowIfKey() == null || f.getShowIfKey().isBlank()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", f.getShowIfKey());
        m.put("values", showIfValues(f));
        return m;
    }

    /**
     * Whether a question is on the form for these answers: always, unless it is a follow-up whose
     * question (itself shown, active) wasn't answered with one of its trigger options.
     * A follow-up whose question was removed is hidden.
     */
    public boolean isShown(RegistrationField f, Map<String, RegistrationField> activeByKey, Map<String, Object> answers) {
        RegistrationField cur = f;
        for (int depth = 0; depth < 20; depth++) {
            String parentKey = cur.getShowIfKey();
            if (parentKey == null || parentKey.isBlank()) return true;
            RegistrationField parent = activeByKey.get(parentKey);
            if (parent == null || parent == f) return false;
            if (!answerMatches(answers.get(parentKey), showIfValues(cur))) return false;
            cur = parent;
        }
        return false; // a loop of conditions; never shown
    }

    private static boolean answerMatches(Object answer, List<String> values) {
        if (answer == null || values.isEmpty()) return false;
        if (answer instanceof Collection<?> c) {
            for (Object o : c) if (o != null && values.contains(String.valueOf(o).trim())) return true;
            return false;
        }
        return values.contains(String.valueOf(answer).trim());
    }

    public List<Map<String, Object>> activeFieldDtos(String programId) {
        if (programId == null) return List.of();
        return repository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId)
            .stream().map(this::toDto).toList();
    }

    public boolean isActiveKey(String programId, String key) {
        return programId != null && repository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId)
            .stream().anyMatch(f -> f.getFieldKey().equals(key));
    }

    public Map<String, Object> parseAnswers(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    public String toJson(Map<String, Object> answers) {
        if (answers == null || answers.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(answers); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid answers"); }
    }

    /**
     * Validates answers against the program's active questions and returns a normalized map
     * containing only known keys. Unknown keys are dropped.
     *
     * @param enforceRequired true for attendee registration; false for admin edits (admins may leave blanks)
     */
    public Map<String, Object> validate(String programId, Map<String, Object> answers, boolean enforceRequired) {
        Map<String, Object> in = answers == null ? Map.of() : answers;
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        if (programId == null) return out;

        List<RegistrationField> active = repository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId);
        Map<String, RegistrationField> activeByKey = new HashMap<>();
        for (RegistrationField f : active) activeByKey.put(f.getFieldKey(), f);

        for (RegistrationField f : active) {
            String key = f.getFieldKey();
            // Attendee registration: a follow-up that doesn't apply isn't required and its answer is dropped.
            // Admin edits send only the changed answers, so there the condition isn't checked.
            if (enforceRequired && !isShown(f, activeByKey, in)) continue;
            Object raw = in.get(key);
            boolean blank = raw == null
                || (raw instanceof String s && s.isBlank())
                || (raw instanceof Collection<?> c && c.isEmpty());
            boolean required = Boolean.TRUE.equals(f.getRequired());

            if ("checkbox".equals(f.getType())) {
                boolean checked = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
                if (required && enforceRequired && !checked) { errors.put(key, f.getLabel() + " is required"); continue; }
                if (raw != null) out.put(key, checked);
                continue;
            }
            if (blank) {
                if (required && enforceRequired) errors.put(key, f.getLabel() + " is required");
                continue;
            }
            try {
                out.put(key, normalize(f, raw));
            } catch (IllegalArgumentException e) {
                errors.put(key, e.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new InvalidAnswersException(errors);
        return out;
    }

    private Object normalize(RegistrationField f, Object raw) {
        String label = f.getLabel();
        switch (f.getType()) {
            case "text", "textarea" -> {
                String s = String.valueOf(raw).trim();
                if (s.length() > MAX_TEXT) throw new IllegalArgumentException(label + " is too long");
                return s;
            }
            case "number" -> {
                try { return new BigDecimal(String.valueOf(raw).trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be a number"); }
            }
            case "date" -> {
                String s = String.valueOf(raw).trim();
                try { LocalDate.parse(s); return s; }
                catch (Exception e) { throw new IllegalArgumentException(label + " must be a date (YYYY-MM-DD)"); }
            }
            case "phone" -> {
                String s = String.valueOf(raw).replaceAll("[\\s().-]", "");
                if (!PHONE.matcher(s).matches()) throw new IllegalArgumentException(label + ": enter a valid phone number with country code");
                return s;
            }
            case "email" -> {
                String s = String.valueOf(raw).trim();
                if (s.length() > 255 || !EMAIL.matcher(s).matches()) throw new IllegalArgumentException(label + ": enter a valid email address");
                return s;
            }
            case "select", "radio" -> {
                String s = String.valueOf(raw).trim();
                if (!options(f).contains(s)) throw new IllegalArgumentException(label + ": choose one of the listed options");
                return s;
            }
            case "multiselect" -> {
                if (!(raw instanceof Collection<?> c)) throw new IllegalArgumentException(label + ": invalid selection");
                List<String> opts = options(f);
                List<String> picked = new ArrayList<>();
                for (Object o : c) {
                    String s = String.valueOf(o).trim();
                    if (!opts.contains(s)) throw new IllegalArgumentException(label + ": choose from the listed options");
                    if (!picked.contains(s)) picked.add(s);
                }
                return picked;
            }
            default -> throw new IllegalArgumentException(label + ": unsupported question type");
        }
    }

    /** Builds a unique, stable key from a label: "T-Shirt Size" → "t_shirt_size" (then _2, _3…). */
    public String uniqueKey(String programId, String label) {
        String base = label == null ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "question";
        if (base.length() > 40) base = base.substring(0, 40);
        String key = base;
        int n = 2;
        while (repository.existsByProgramIdAndFieldKey(programId, key)) key = base + "_" + n++;
        return key;
    }
}
