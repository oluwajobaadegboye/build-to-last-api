package com.btl.transport.registration;

import com.btl.transport.admin.AdminAccess;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Admin CRUD for a program's custom registration questions (program = X-Program-Id). */
@Tag(name = "Registration fields", description = "Custom questions asked at registration")
@RestController
@RequestMapping("/api/v1/admin/registration-fields")
@RequiredArgsConstructor
public class RegistrationFieldController {

    private final RegistrationFieldRepository repository;
    private final RegistrationFieldService service;
    private final AdminAccess adminAccess;

    public record FieldRequest(
        String label,
        String type,
        List<String> options,
        Boolean required,
        @JsonProperty("help_text") String helpText
    ) {}

    public record OrderRequest(List<Integer> ids) {}

    private static String requireProgram(String programId) {
        if (programId == null || programId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        }
        return programId;
    }

    private RegistrationField load(Integer id, String programId) {
        RegistrationField f = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(f.getProgramId());
        if (programId != null && !programId.equals(f.getProgramId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        return f;
    }

    private void validateDefinition(String label, String type, List<String> options) {
        if (label == null || label.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question text is required");
        if (label.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question text is too long");
        if (type == null || !RegistrationFieldService.TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown question type");
        }
        boolean choice = type.equals("select") || type.equals("multiselect");
        long usable = options == null ? 0 : options.stream().filter(o -> o != null && !o.isBlank()).count();
        if (choice && usable < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one option");
    }

    @Operation(summary = "List questions", description = "All questions for the program, including removed ones (active=false)")
    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return repository.findByProgramIdOrderBySortOrderAscIdAsc(requireProgram(programId))
            .stream().map(service::toDto).toList();
    }

    @Operation(summary = "Add question")
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @RequestBody FieldRequest req) {
        String pid = requireProgram(programId);
        validateDefinition(req.label(), req.type(), req.options());
        int nextOrder = repository.findByProgramIdOrderBySortOrderAscIdAsc(pid).stream()
            .mapToInt(RegistrationField::getSortOrder).max().orElse(-1) + 1;
        boolean choice = req.type().equals("select") || req.type().equals("multiselect");
        RegistrationField f = RegistrationField.builder()
            .programId(pid)
            .fieldKey(service.uniqueKey(pid, req.label()))
            .label(req.label().trim())
            .type(req.type())
            .options(choice ? service.optionsJson(req.options()) : null)
            .required(Boolean.TRUE.equals(req.required()))
            .helpText(req.helpText() == null || req.helpText().isBlank() ? null : req.helpText().trim())
            .sortOrder(nextOrder)
            .build();
        return service.toDto(repository.save(f));
    }

    @Operation(summary = "Edit question", description = "The answer key never changes, so existing answers stay attached")
    @PatchMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @PathVariable Integer id, @RequestBody FieldRequest req) {
        RegistrationField f = load(id, programId);
        String label = req.label() != null ? req.label() : f.getLabel();
        String type = req.type() != null ? req.type() : f.getType();
        List<String> options = req.options() != null ? req.options() : service.options(f);
        validateDefinition(label, type, options);
        boolean choice = type.equals("select") || type.equals("multiselect");
        f.setLabel(label.trim());
        f.setType(type);
        f.setOptions(choice ? service.optionsJson(options) : null);
        if (req.required() != null) f.setRequired(req.required());
        if (req.helpText() != null) f.setHelpText(req.helpText().isBlank() ? null : req.helpText().trim());
        return service.toDto(repository.save(f));
    }

    @Operation(summary = "Remove question", description = "Hides it from the form; attendees' existing answers are kept")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> remove(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                                      @PathVariable Integer id) {
        RegistrationField f = load(id, programId);
        f.setActive(false);
        repository.save(f);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Restore question", description = "Puts a removed question back on the form")
    @PostMapping("/{id}/restore")
    @Transactional
    public Map<String, Object> restore(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                       @PathVariable Integer id) {
        RegistrationField f = load(id, programId);
        f.setActive(true);
        return service.toDto(repository.save(f));
    }

    @Operation(summary = "Reorder questions", description = "Body: { ids: [...] } in the desired order")
    @PutMapping("/order")
    @Transactional
    public List<Map<String, Object>> reorder(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                             @RequestBody OrderRequest req) {
        String pid = requireProgram(programId);
        List<RegistrationField> all = repository.findByProgramIdOrderBySortOrderAscIdAsc(pid);
        if (req.ids() != null) {
            for (int i = 0; i < req.ids().size(); i++) {
                Integer wanted = req.ids().get(i);
                int order = i;
                all.stream().filter(f -> f.getId().equals(wanted)).findFirst()
                    .ifPresent(f -> f.setSortOrder(order));
            }
            repository.saveAll(all);
        }
        return repository.findByProgramIdOrderBySortOrderAscIdAsc(pid).stream().map(service::toDto).toList();
    }
}
