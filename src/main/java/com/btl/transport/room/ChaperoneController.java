package com.btl.transport.room;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.admin.Audit;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Admin: turn chaperones on/off and assign them to rooms and hostels (program = X-Program-Id). */
@Tag(name = "Chaperones", description = "Attendees who look after a room or a hostel")
@RestController
@RequestMapping("/api/v1/admin/chaperones")
@RequiredArgsConstructor
public class ChaperoneController {

    private final ProgramRepository programRepository;
    private final RoomAssignmentRepository roomRepository;
    private final HotelChaperoneRepository hotelChaperoneRepository;
    private final ParticipantRepository participantRepository;
    private final AdminAccess adminAccess;
    private final ChaperoneService chaperoneService;

    public record EnabledRequest(Boolean enabled) {}
    /** reg_code = the chaperone's registration code; null/empty = no chaperone. */
    public record RoomRequest(@JsonProperty("reg_code") String regCode) {}
    public record HotelRequest(@JsonProperty("hotel_name") String hotelName,
                               @JsonProperty("reg_code") String regCode,
                               String gender) {}

    private Program program(String programId) {
        if (programId == null || programId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        adminAccess.requireProgram(programId);
        return programRepository.findById(programId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
    }

    private Participant attendee(String code, String programId) {
        if (code == null || code.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a registered attendee");
        return participantRepository.findByRegCode(code.trim())
            .filter(p -> programId.equals(p.getProgramId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That person isn't registered for this program"));
    }

    private static String cleanGender(String g) {
        if (g == null || g.isBlank()) return null;
        String v = g.trim().toLowerCase();
        if (!v.equals("male") && !v.equals("female")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gender must be male, female or empty");
        return v;
    }

    private Map<String, Object> state(Program prog) {
        String pid = prog.getId();
        Map<Integer, Participant> people = new HashMap<>();
        for (Participant p : participantRepository.findAllByProgramId(pid)) people.put(p.getId(), p);
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (RoomAssignment r : roomRepository.findByProgramId(pid)) {
            if (r.getChaperoneParticipantId() == null) continue;
            Participant c = people.get(r.getChaperoneParticipantId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("room_id", r.getId());
            m.put("reg_code", c != null ? c.getRegCode() : null);
            m.put("name", c != null ? c.getFullName() : null);
            m.put("participant_gender", c != null ? c.getGender() : null);
            rooms.add(m);
        }
        List<Map<String, Object>> hotels = new ArrayList<>();
        for (HotelChaperone hc : hotelChaperoneRepository.findByProgramIdOrderByHotelNameAscIdAsc(pid)) {
            Participant c = people.get(hc.getParticipantId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", hc.getId());
            m.put("hotel_name", hc.getHotelName());
            m.put("reg_code", c != null ? c.getRegCode() : null);
            m.put("name", c != null ? c.getFullName() : null);
            m.put("participant_gender", c != null ? c.getGender() : null);
            m.put("gender", hc.getGender());
            hotels.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", Boolean.TRUE.equals(prog.getChaperonesEnabled()));
        out.put("rooms", rooms);
        out.put("hotels", hotels);
        return out;
    }

    @Operation(summary = "Chaperone settings", description = "Whether chaperones are on, plus room and hostel chaperones")
    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return state(program(programId));
    }

    @Operation(summary = "Chaperone groups", description = "Each chaperone with the people in their care (rooms and hostels), for the printable lists")
    @GetMapping("/groups")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> groups(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        Program prog = program(programId);
        Set<Integer> ids = new LinkedHashSet<>();
        for (RoomAssignment r : roomRepository.findByProgramId(prog.getId())) if (r.getChaperoneParticipantId() != null) ids.add(r.getChaperoneParticipantId());
        for (HotelChaperone hc : hotelChaperoneRepository.findByProgramIdOrderByHotelNameAscIdAsc(prog.getId())) ids.add(hc.getParticipantId());
        List<Map<String, Object>> out = new ArrayList<>();
        List<Participant> chaperones = participantRepository.findAllById(ids).stream().filter(c -> prog.getId().equals(c.getProgramId())).toList();
        Map<Integer, List<Map<String, Object>>> careGroups = chaperoneService.careGroupsFor(prog.getId(), chaperones);
        for (Participant c : chaperones) {
            List<Map<String, Object>> groups = careGroups.get(c.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reg_code", c.getRegCode());
            m.put("name", c.getFullName());
            m.put("phone", c.getPhone() != null ? c.getPhone() : "");
            m.put("email", c.getEmail() != null ? c.getEmail() : "");
            m.put("gender", c.getGender());
            m.put("groups", groups != null ? groups : List.of());
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase()));
        return out;
    }

    @Operation(summary = "Turn chaperones on or off")
    @PutMapping("/enabled")
    @Transactional
    public Map<String, Object> setEnabled(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                          @RequestBody EnabledRequest req) {
        Program prog = program(programId);
        prog.setChaperonesEnabled(Boolean.TRUE.equals(req.enabled()));
        Audit.detail(Boolean.TRUE.equals(req.enabled()) ? "On" : "Off");
        programRepository.save(prog);
        return state(prog);
    }

    @Operation(summary = "Set a room's chaperone", description = "reg_code empty = no chaperone")
    @PutMapping("/rooms/{roomId}")
    @Transactional
    public Map<String, Object> setRoom(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                       @PathVariable Integer roomId, @RequestBody RoomRequest req) {
        Program prog = program(programId);
        RoomAssignment room = roomRepository.findById(roomId)
            .filter(r -> prog.getId().equals(r.getProgramId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (req.regCode() == null || req.regCode().isBlank()) {
            room.setChaperoneParticipantId(null);
            Audit.subject("Room " + room.getRoomLabel());
        } else {
            Participant c = attendee(req.regCode(), prog.getId());
            room.setChaperoneParticipantId(c.getId());
            Audit.subject("Room " + room.getRoomLabel());
            Audit.detail(c.getFullName());
        }
        roomRepository.save(room);
        return state(prog);
    }

    @Operation(summary = "Add a hostel chaperone")
    @PostMapping("/hotels")
    @Transactional
    public Map<String, Object> addHotel(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                        @RequestBody HotelRequest req) {
        Program prog = program(programId);
        String hotel = req.hotelName() == null ? "" : req.hotelName().trim();
        if (hotel.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a hostel");
        Participant c = attendee(req.regCode(), prog.getId());
        boolean exists = hotelChaperoneRepository.findByProgramIdOrderByHotelNameAscIdAsc(prog.getId()).stream()
            .anyMatch(h -> h.getHotelName().equalsIgnoreCase(hotel) && h.getParticipantId().equals(c.getId()));
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, c.getFullName() + " is already a chaperone here");
        hotelChaperoneRepository.save(HotelChaperone.builder()
            .programId(prog.getId()).hotelName(hotel).participantId(c.getId()).gender(cleanGender(req.gender())).build());
        Audit.subject(hotel);
        Audit.detail(c.getFullName());
        return state(prog);
    }

    @Operation(summary = "Change who a hostel chaperone is for", description = "gender: male, female or empty (everyone)")
    @PatchMapping("/hotels/{id}")
    @Transactional
    public Map<String, Object> updateHotel(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                           @PathVariable Integer id, @RequestBody HotelRequest req) {
        Program prog = program(programId);
        HotelChaperone hc = hotelChaperoneRepository.findById(id)
            .filter(h -> prog.getId().equals(h.getProgramId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        hc.setGender(cleanGender(req.gender()));
        hotelChaperoneRepository.save(hc);
        Audit.subject(hc.getHotelName());
        return state(prog);
    }

    @Operation(summary = "Remove a hostel chaperone")
    @DeleteMapping("/hotels/{id}")
    @Transactional
    public Map<String, Object> removeHotel(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                           @PathVariable Integer id) {
        Program prog = program(programId);
        HotelChaperone hc = hotelChaperoneRepository.findById(id)
            .filter(h -> prog.getId().equals(h.getProgramId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        hotelChaperoneRepository.delete(hc);
        Audit.subject(hc.getHotelName());
        return state(prog);
    }
}
