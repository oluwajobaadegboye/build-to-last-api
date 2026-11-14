package com.btl.transport.registration;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.admin.Audit;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extra steps of registration questions (program = X-Program-Id). The first step isn't stored here:
 * it holds every question without a section and is titled by programs.questions_title.
 */
@Tag(name = "Registration sections", description = "Split registration questions into several steps")
@RestController
@RequestMapping("/api/v1/admin/registration-sections")
@RequiredArgsConstructor
public class RegistrationSectionController {

    private final RegistrationSectionRepository repository;
    private final RegistrationFieldRepository fieldRepository;
    private final ProgramRepository programRepository;
    private final AdminAccess adminAccess;

    public record SectionRequest(String title) {}
    public record OrderRequest(List<Integer> ids) {}
    /** "" = first step, "own" = a "Souvenirs" step of their own, otherwise a section id. */
    public record SouvenirStepRequest(String step) {}

    private static String requireProgram(String programId) {
        if (programId == null || programId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        return programId;
    }

    private static String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the section a title");
        if (t.length() > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section title must be 60 characters or fewer");
        return t;
    }

    private RegistrationSection load(Integer id, String programId) {
        RegistrationSection s = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(s.getProgramId());
        if (programId != null && !programId.equals(s.getProgramId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        return s;
    }

    private Program program(String programId) {
        return programRepository.findById(programId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
    }

    public static Map<String, Object> toDto(RegistrationSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("sort_order", s.getSortOrder());
        return m;
    }

    private Map<String, Object> state(String programId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sections", repository.findByProgramIdOrderBySortOrderAscIdAsc(programId).stream().map(RegistrationSectionController::toDto).toList());
        m.put("souvenir_step", program(programId).getSouvenirStep());
        return m;
    }

    @Operation(summary = "List sections", description = "Extra question steps in order, plus where souvenir questions are asked")
    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return state(requireProgram(programId));
    }

    @Operation(summary = "Add section", description = "Added after the existing steps")
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @RequestBody SectionRequest req) {
        String pid = requireProgram(programId);
        String title = cleanTitle(req.title());
        Audit.subject(title);
        int next = repository.findByProgramIdOrderBySortOrderAscIdAsc(pid).stream()
            .mapToInt(RegistrationSection::getSortOrder).max().orElse(-1) + 1;
        return toDto(repository.save(RegistrationSection.builder().programId(pid).title(title).sortOrder(next).build()));
    }

    @Operation(summary = "Rename section")
    @PatchMapping("/{id}")
    @Transactional
    public Map<String, Object> rename(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @PathVariable Integer id, @RequestBody SectionRequest req) {
        RegistrationSection s = load(id, programId);
        s.setTitle(cleanTitle(req.title()));
        Audit.subject(s.getTitle());
        return toDto(repository.save(s));
    }

    @Operation(summary = "Remove section", description = "Its questions move to the first step; nothing is deleted")
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> remove(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @PathVariable Integer id) {
        RegistrationSection s = load(id, programId);
        Audit.subject(s.getTitle());
        List<RegistrationField> moved = fieldRepository.findByProgramIdOrderBySortOrderAscIdAsc(s.getProgramId()).stream()
            .filter(f -> id.equals(f.getSectionId())).toList();
        moved.forEach(f -> f.setSectionId(null));
        fieldRepository.saveAll(moved);
        Program p = program(s.getProgramId());
        if (String.valueOf(id).equals(p.getSouvenirStep())) { p.setSouvenirStep(null); programRepository.save(p); }
        repository.delete(s);
        return state(s.getProgramId());
    }

    @Operation(summary = "Reorder sections", description = "Body: { ids: [...] } in the desired order")
    @PutMapping("/order")
    @Transactional
    public Map<String, Object> reorder(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                       @RequestBody OrderRequest req) {
        String pid = requireProgram(programId);
        List<RegistrationSection> all = repository.findByProgramIdOrderBySortOrderAscIdAsc(pid);
        if (req.ids() != null) {
            for (int i = 0; i < req.ids().size(); i++) {
                Integer wanted = req.ids().get(i);
                int order = i;
                all.stream().filter(s -> s.getId().equals(wanted)).findFirst().ifPresent(s -> s.setSortOrder(order));
            }
            repository.saveAll(all);
        }
        return state(pid);
    }

    @Operation(summary = "Choose where souvenir questions go", description = "step: \"\" = first step, \"own\" = their own step, or a section id")
    @PutMapping("/souvenirs")
    @Transactional
    public Map<String, Object> souvenirStep(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                            @RequestBody SouvenirStepRequest req) {
        String pid = requireProgram(programId);
        String step = req.step() == null ? "" : req.step().trim();
        if (!step.isEmpty() && !step.equals("own")) {
            Integer id;
            try { id = Integer.valueOf(step); } catch (NumberFormatException e) { id = null; }
            Integer sid = id;
            if (sid == null || repository.findById(sid).filter(s -> s.getProgramId().equals(pid)).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That section doesn't exist");
            }
        }
        Program p = program(pid);
        p.setSouvenirStep(step.isEmpty() ? null : step);
        programRepository.save(p);
        return state(pid);
    }
}
