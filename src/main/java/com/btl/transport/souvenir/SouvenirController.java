package com.btl.transport.souvenir;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Souvenir items and hand-outs (permission checks: JwtAuthFilter + AdminPermissions). Program = X-Program-Id. */
@Tag(name = "Souvenirs", description = "Items a program gives out, attendees' sizes and who has received what")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class SouvenirController {

    private final SouvenirItemRepository itemRepository;
    private final SouvenirService service;
    private final ParticipantRepository participantRepository;
    private final AdminAccess adminAccess;

    public record ItemRequest(
        String name,
        List<String> options,
        @JsonProperty("ask_at_registration") Boolean askAtRegistration
    ) {}

    /** received: null = unchanged; choice: null = unchanged, "" = clear. */
    public record UpdateRequest(Boolean received, String choice) {}

    public record OrderRequest(List<Integer> ids) {}

    private static String requireProgram(String programId) {
        if (programId == null || programId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        return programId;
    }

    private SouvenirItem loadItem(Integer id, String programId) {
        SouvenirItem item = itemRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(item.getProgramId());
        if (programId != null && !programId.equals(item.getProgramId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        return item;
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name is required");
        String n = name.trim();
        if (n.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name is too long");
        return n;
    }

    // ── Items ───────────────────────────────────────────────────────────────

    @Operation(summary = "List items", description = "All souvenir items, including removed ones (active=false)")
    @GetMapping("/souvenirs")
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return itemRepository.findByProgramIdOrderBySortOrderAscIdAsc(requireProgram(programId)).stream().map(service::itemDto).toList();
    }

    @Operation(summary = "Add item", description = "Body: { name, options?: [\"S\",\"M\"], ask_at_registration? }")
    @PostMapping("/souvenirs")
    @Transactional
    public Map<String, Object> create(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @RequestBody ItemRequest req) {
        String pid = requireProgram(programId);
        int next = itemRepository.findByProgramIdOrderBySortOrderAscIdAsc(pid).stream()
            .mapToInt(SouvenirItem::getSortOrder).max().orElse(-1) + 1;
        SouvenirItem item = SouvenirItem.builder()
            .programId(pid)
            .name(cleanName(req.name()))
            .options(service.optionsJson(req.options()))
            .askAtRegistration(Boolean.TRUE.equals(req.askAtRegistration()))
            .sortOrder(next)
            .build();
        return service.itemDto(itemRepository.save(item));
    }

    @Operation(summary = "Edit item", description = "Existing choices and hand-outs are kept, even for options removed from the list")
    @PatchMapping("/souvenirs/{id}")
    @Transactional
    public Map<String, Object> update(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @PathVariable Integer id, @RequestBody ItemRequest req) {
        SouvenirItem item = loadItem(id, programId);
        if (req.name() != null) item.setName(cleanName(req.name()));
        if (req.options() != null) item.setOptions(service.optionsJson(req.options()));
        if (req.askAtRegistration() != null) item.setAskAtRegistration(req.askAtRegistration());
        return service.itemDto(itemRepository.save(item));
    }

    @Operation(summary = "Remove item", description = "Hides it everywhere; who received it is kept and comes back if restored")
    @DeleteMapping("/souvenirs/{id}")
    @Transactional
    public Map<String, Object> remove(@RequestHeader(value = "X-Program-Id", required = false) String programId, @PathVariable Integer id) {
        SouvenirItem item = loadItem(id, programId);
        item.setActive(false);
        return service.itemDto(itemRepository.save(item));
    }

    @Operation(summary = "Restore item")
    @PostMapping("/souvenirs/{id}/restore")
    @Transactional
    public Map<String, Object> restore(@RequestHeader(value = "X-Program-Id", required = false) String programId, @PathVariable Integer id) {
        SouvenirItem item = loadItem(id, programId);
        item.setActive(true);
        return service.itemDto(itemRepository.save(item));
    }

    @Operation(summary = "Reorder items", description = "Body: { ids: [...] } in the desired order")
    @PutMapping("/souvenirs/order")
    @Transactional
    public List<Map<String, Object>> reorder(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                             @RequestBody OrderRequest req) {
        String pid = requireProgram(programId);
        List<SouvenirItem> all = itemRepository.findByProgramIdOrderBySortOrderAscIdAsc(pid);
        if (req.ids() != null) {
            for (int i = 0; i < req.ids().size(); i++) {
                Integer wanted = req.ids().get(i);
                int order = i;
                all.stream().filter(x -> x.getId().equals(wanted)).findFirst().ifPresent(x -> x.setSortOrder(order));
            }
            itemRepository.saveAll(all);
        }
        return itemRepository.findByProgramIdOrderBySortOrderAscIdAsc(pid).stream().map(service::itemDto).toList();
    }

    // ── Hand-outs ───────────────────────────────────────────────────────────

    @Operation(summary = "Hand-out sheet", description = "Active items and every participant with their choice and received state per item")
    @GetMapping("/souvenirs/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        String pid = requireProgram(programId);
        List<SouvenirItem> items = itemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(pid);
        Map<Integer, Map<Integer, ParticipantSouvenir>> records = service.recordsForProgram(pid);
        List<Map<String, Object>> people = new ArrayList<>();
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(pid)) {
            Map<Integer, ParticipantSouvenir> mine = records.getOrDefault(p.getId(), Map.of());
            Map<String, Object> perItem = new LinkedHashMap<>();
            for (SouvenirItem item : items) {
                ParticipantSouvenir r = mine.get(item.getId());
                if (r != null) perItem.put(String.valueOf(item.getId()), service.recordDto(r));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("btl_code", p.getBtlCode());
            m.put("full_name", p.getFullName());
            m.put("email", p.getEmail());
            m.put("phone", p.getPhone());
            m.put("souvenirs", perItem);
            people.add(m);
        }
        people.sort(Comparator.comparing(m -> String.valueOf(m.get("full_name")).toLowerCase(Locale.ROOT)));
        return Map.of("items", items.stream().map(service::itemDto).toList(), "participants", people);
    }

    @Operation(summary = "Update a participant's item", description = "Body: { received?: true|false, choice?: \"L\" (\"\" clears) }")
    @PutMapping("/participants/{code}/souvenirs/{itemId}")
    public Map<String, Object> updateParticipant(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                                 @PathVariable String code, @PathVariable Integer itemId,
                                                 @RequestBody UpdateRequest req) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getProgramId());
        SouvenirItem item = loadItem(itemId, p.getProgramId());
        try {
            return service.update(p, item, req.received(), req.choice(), adminAccess.currentUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
