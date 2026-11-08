package com.btl.transport.souvenir;

import com.btl.transport.participant.Participant;
import com.btl.transport.registration.RegistrationFieldService.InvalidAnswersException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** Souvenir items, attendees' choices (e.g. sizes) and hand-outs. */
@Service
@RequiredArgsConstructor
public class SouvenirService {

    /** Prefix for registration errors so the form can show them next to the right question. */
    public static final String ERROR_KEY_PREFIX = "souvenir:";

    private final SouvenirItemRepository itemRepository;
    private final ParticipantSouvenirRepository recordRepository;
    private final ObjectMapper objectMapper;

    // ── Items ───────────────────────────────────────────────────────────────

    public List<String> options(SouvenirItem item) {
        if (item.getOptions() == null || item.getOptions().isBlank()) return List.of();
        try { return objectMapper.readValue(item.getOptions(), new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    public String optionsJson(List<String> options) {
        if (options == null) return null;
        List<String> clean = options.stream().filter(Objects::nonNull).map(String::trim)
            .filter(s -> !s.isEmpty()).distinct().toList();
        if (clean.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(clean); } catch (Exception e) { return null; }
    }

    /** Only items with options can be asked at registration. */
    public boolean asksAtRegistration(SouvenirItem item) {
        return Boolean.TRUE.equals(item.getActive()) && Boolean.TRUE.equals(item.getAskAtRegistration()) && !options(item).isEmpty();
    }

    public Map<String, Object> itemDto(SouvenirItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("name", item.getName());
        m.put("options", options(item));
        m.put("ask_at_registration", Boolean.TRUE.equals(item.getAskAtRegistration()));
        m.put("active", Boolean.TRUE.equals(item.getActive()));
        m.put("sort_order", item.getSortOrder());
        return m;
    }

    /** Items attendees choose an option for when registering (public program info). */
    public List<Map<String, Object>> registrationItems(String programId) {
        if (programId == null) return List.of();
        return itemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId).stream()
            .filter(this::asksAtRegistration)
            .map(i -> Map.<String, Object>of("id", i.getId(), "name", i.getName(), "options", options(i)))
            .toList();
    }

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Checks an attendee's choices against the items asked at registration: each is required and must be
     * one of the item's options. Unknown items are ignored. Errors are keyed "souvenir:{itemId}".
     */
    public Map<Integer, String> validateChoices(String programId, Map<String, String> choices) {
        Map<String, String> in = choices == null ? Map.of() : choices;
        Map<Integer, String> out = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        if (programId == null) return out;
        for (SouvenirItem item : itemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId)) {
            if (!asksAtRegistration(item)) continue;
            String raw = in.get(String.valueOf(item.getId()));
            String v = raw == null ? "" : raw.trim();
            if (v.isEmpty()) { errors.put(ERROR_KEY_PREFIX + item.getId(), "Choose your " + item.getName() + " option"); continue; }
            if (!options(item).contains(v)) { errors.put(ERROR_KEY_PREFIX + item.getId(), item.getName() + ": choose one of the listed options"); continue; }
            out.put(item.getId(), v);
        }
        if (!errors.isEmpty()) throw new InvalidAnswersException(errors);
        return out;
    }

    /** Stores the attendee's choices (keeps any hand-out already recorded). */
    @Transactional
    public void saveChoices(Participant p, Map<Integer, String> choices) {
        if (choices == null || choices.isEmpty()) return;
        for (Map.Entry<Integer, String> e : choices.entrySet()) {
            ParticipantSouvenir r = recordRepository.findByParticipantIdAndItemId(p.getId(), e.getKey())
                .orElseGet(() -> ParticipantSouvenir.builder().participantId(p.getId()).itemId(e.getKey()).programId(p.getProgramId()).build());
            r.setChoice(e.getValue());
            recordRepository.save(r);
        }
    }

    // ── Hand-outs ───────────────────────────────────────────────────────────

    public Map<String, Object> recordDto(ParticipantSouvenir r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("choice", r.getChoice());
        m.put("received", Boolean.TRUE.equals(r.getReceived()));
        m.put("received_at", r.getReceivedAt() != null ? r.getReceivedAt().toString() : null);
        m.put("received_by", r.getReceivedBy());
        return m;
    }

    /**
     * Updates one participant's item. {@code received} null = unchanged; {@code choice} null = unchanged,
     * blank = cleared. The choice must be one of the item's options (when it has any).
     */
    @Transactional
    public Map<String, Object> update(Participant p, SouvenirItem item, Boolean received, String choice, String by) {
        ParticipantSouvenir r = recordRepository.findByParticipantIdAndItemId(p.getId(), item.getId())
            .orElseGet(() -> ParticipantSouvenir.builder().participantId(p.getId()).itemId(item.getId()).programId(p.getProgramId()).build());
        if (choice != null) {
            String c = choice.trim();
            if (c.isEmpty()) r.setChoice(null);
            else {
                List<String> opts = options(item);
                if (!opts.isEmpty() && !opts.contains(c)) throw new IllegalArgumentException("Choose one of: " + String.join(", ", opts));
                r.setChoice(c);
            }
        }
        if (received != null && received != Boolean.TRUE.equals(r.getReceived())) {
            r.setReceived(received);
            r.setReceivedAt(received ? OffsetDateTime.now() : null);
            r.setReceivedBy(received ? by : null);
        }
        return recordDto(recordRepository.save(r));
    }

    /** item id → record, for one participant. */
    public Map<Integer, ParticipantSouvenir> recordsFor(Integer participantId) {
        Map<Integer, ParticipantSouvenir> m = new HashMap<>();
        for (ParticipantSouvenir r : recordRepository.findByParticipantId(participantId)) m.put(r.getItemId(), r);
        return m;
    }

    /** participant id → (item id → record), for a whole program. */
    public Map<Integer, Map<Integer, ParticipantSouvenir>> recordsForProgram(String programId) {
        Map<Integer, Map<Integer, ParticipantSouvenir>> m = new HashMap<>();
        for (ParticipantSouvenir r : recordRepository.findByProgramId(programId)) {
            m.computeIfAbsent(r.getParticipantId(), k -> new HashMap<>()).put(r.getItemId(), r);
        }
        return m;
    }

    /** What the attendee sees: every active item, their choice and whether they've collected it. */
    public List<Map<String, Object>> attendeeView(Participant p) {
        if (p.getProgramId() == null) return List.of();
        Map<Integer, ParticipantSouvenir> mine = recordsFor(p.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (SouvenirItem item : itemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(p.getProgramId())) {
            ParticipantSouvenir r = mine.get(item.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", item.getName());
            m.put("choice", r != null ? r.getChoice() : null);
            m.put("received", r != null && Boolean.TRUE.equals(r.getReceived()));
            out.add(m);
        }
        return out;
    }
}
