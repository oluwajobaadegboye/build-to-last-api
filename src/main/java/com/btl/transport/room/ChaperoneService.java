package com.btl.transport.room;

import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Chaperones as attendees see them: who looks after me (room chaperone, then hostel chaperones),
 * and — for a chaperone — the people in their care. Everything is empty when the program has
 * chaperones turned off.
 *
 * <p>An attendee's hostel is their room's hostel, else the one they picked when registering, else the
 * program's only hostel (the same rule the status page uses for directions).
 */
@Service
@RequiredArgsConstructor
public class ChaperoneService {

    private final ProgramRepository programRepository;
    private final RoomAssignmentRepository roomRepository;
    private final HotelChaperoneRepository hotelChaperoneRepository;
    private final ParticipantRepository participantRepository;
    private final HotelRepository hotelRepository;

    public boolean enabled(String programId) {
        return programId != null && programRepository.findById(programId)
            .map(Program::getChaperonesEnabled).map(Boolean.TRUE::equals).orElse(false);
    }

    /** Everything needed to answer "who's where" for one program, loaded once. */
    private final class Snapshot {
        final List<RoomAssignment> rooms;
        final Map<Integer, RoomAssignment> roomOfParticipant = new HashMap<>();
        final Map<String, RoomAssignment> roomOfEmail = new HashMap<>();
        final List<HotelChaperone> hotelChaperones;
        /** Just the chaperones, by id (small); the whole program is loaded only when a hostel group needs it. */
        final Map<Integer, Participant> people = new HashMap<>();
        private final String programId;
        private List<Participant> everyone;
        final String onlyHotel;

        Snapshot(String programId) {
            rooms = roomRepository.findByProgramIdWithOccupants(programId);
            for (RoomAssignment r : rooms) {
                for (RoomOccupant o : r.getOccupants()) {
                    if (o.getParticipant() != null) roomOfParticipant.putIfAbsent(o.getParticipant().getId(), r);
                    else if (o.getEmail() != null && !o.getEmail().isBlank()) roomOfEmail.putIfAbsent(o.getEmail().trim().toLowerCase(), r);
                }
            }
            this.programId = programId;
            hotelChaperones = hotelChaperoneRepository.findByProgramIdOrderByHotelNameAscIdAsc(programId);
            Set<Integer> chaperoneIds = new HashSet<>();
            for (RoomAssignment r : rooms) if (r.getChaperoneParticipantId() != null) chaperoneIds.add(r.getChaperoneParticipantId());
            for (HotelChaperone hc : hotelChaperones) chaperoneIds.add(hc.getParticipantId());
            if (!chaperoneIds.isEmpty()) for (Participant p : participantRepository.findAllById(chaperoneIds)) people.put(p.getId(), p);
            List<Hotel> hotels = hotelRepository.findByProgramIdOrderByShuttleStopOrderAsc(programId);
            onlyHotel = hotels.size() == 1 ? hotels.get(0).getHotelName() : null;
        }

        /** Everyone in the program (loaded once, on first use). */
        List<Participant> everyone() {
            if (everyone == null) everyone = participantRepository.findAllByProgramIdWithHotel(programId);
            return everyone;
        }

        RoomAssignment roomOf(Participant p) {
            RoomAssignment r = roomOfParticipant.get(p.getId());
            if (r == null && p.getEmail() != null) r = roomOfEmail.get(p.getEmail().trim().toLowerCase());
            return r;
        }

        String hotelOf(Participant p) {
            RoomAssignment r = roomOf(p);
            if (r != null && r.getHotelName() != null && !r.getHotelName().isBlank()) return r.getHotelName();
            if (p.getHotel() != null && p.getHotel().getHotelName() != null) return p.getHotel().getHotelName();
            return onlyHotel;
        }
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /** A hostel chaperone tagged for one gender is only for attendees of that gender (unknown gender sees all). */
    private static boolean genderFits(String tag, String gender) {
        return tag == null || tag.isBlank() || gender == null || gender.isBlank() || tag.trim().equalsIgnoreCase(gender.trim());
    }

    private static Map<String, Object> contact(Participant c, String role, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getFullName());
        m.put("phone", c.getPhone() != null ? c.getPhone() : "");
        m.put("email", c.getEmail() != null ? c.getEmail() : "");
        m.put("role", role);
        m.put("label", label);
        return m;
    }

    /** For the attendee status page: [their chaperones, their care group or null], from one load of the program. */
    public Object[] attendeeView(Participant p) {
        if (!enabled(p.getProgramId())) return new Object[]{ List.of(), null };
        Snapshot s = new Snapshot(p.getProgramId());
        return new Object[]{ chaperonesFor(p, s), careGroupFor(p, s) };
    }

    /** The attendee's chaperones: their room's, then their hostel's (those for their gender). Never themselves. */
    public List<Map<String, Object>> chaperonesFor(Participant p) {
        if (!enabled(p.getProgramId())) return List.of();
        return chaperonesFor(p, new Snapshot(p.getProgramId()));
    }

    private List<Map<String, Object>> chaperonesFor(Participant p, Snapshot s) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>(Set.of(p.getId()));
        RoomAssignment room = s.roomOf(p);
        if (room != null && room.getChaperoneParticipantId() != null) {
            Participant c = s.people.get(room.getChaperoneParticipantId());
            if (c != null && seen.add(c.getId())) out.add(contact(c, "room", "Room " + room.getRoomLabel()));
        }
        String hotel = s.hotelOf(p);
        if (hotel != null) {
            for (HotelChaperone hc : s.hotelChaperones) {
                if (!sameName(hc.getHotelName(), hotel) || !genderFits(hc.getGender(), p.getGender())) continue;
                Participant c = s.people.get(hc.getParticipantId());
                if (c != null && seen.add(c.getId())) out.add(contact(c, "hotel", hc.getHotelName()));
            }
        }
        return out;
    }

    private static Map<String, Object> person(String name, String phone, String email, String room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("phone", phone != null ? phone : "");
        m.put("email", email != null ? email : "");
        m.put("room", room);
        return m;
    }

    /**
     * For a chaperone: the groups in their care — each room they chaperone (its occupants) and each
     * hostel they chaperone (everyone staying there, or one gender). Null when they chaperone nothing.
     */
    public List<Map<String, Object>> careGroupFor(Participant p) {
        if (!enabled(p.getProgramId())) return null;
        return careGroupFor(p, new Snapshot(p.getProgramId()));
    }

    /** Every chaperone's group for the admin lists, loading the program once (chaperone id → groups). */
    public Map<Integer, List<Map<String, Object>>> careGroupsFor(String programId, Collection<Participant> chaperones) {
        Map<Integer, List<Map<String, Object>>> out = new HashMap<>();
        if (!enabled(programId) || chaperones.isEmpty()) return out;
        Snapshot s = new Snapshot(programId);
        for (Participant c : chaperones) {
            List<Map<String, Object>> g = careGroupFor(c, s);
            out.put(c.getId(), g != null ? g : List.of());
        }
        return out;
    }

    private List<Map<String, Object>> careGroupFor(Participant p, Snapshot s) {
        List<Map<String, Object>> groups = new ArrayList<>();

        for (RoomAssignment r : s.rooms) {
            if (!p.getId().equals(r.getChaperoneParticipantId())) continue;
            List<Map<String, Object>> people = new ArrayList<>();
            for (RoomOccupant o : r.getOccupants()) {
                Participant op = o.getParticipant();
                if (op != null && op.getId().equals(p.getId())) continue;
                if (op == null && sameName(o.getEmail(), p.getEmail())) continue;
                String name = op != null ? op.getFullName() : o.getName();
                if (name == null || name.isBlank()) continue;
                people.add(person(name, op != null ? op.getPhone() : o.getPhone(), op != null ? op.getEmail() : o.getEmail(), r.getRoomLabel()));
            }
            groups.add(group("room", "Room " + r.getRoomLabel() + " · " + r.getHotelName(), people));
        }

        for (HotelChaperone hc : s.hotelChaperones) {
            if (!p.getId().equals(hc.getParticipantId())) continue;
            List<Map<String, Object>> people = new ArrayList<>();
            List<Participant> staying = s.everyone().stream()
                .filter(x -> !x.getId().equals(p.getId()))
                .filter(x -> x.getWaitlistedAt() == null)  // no place yet
                .filter(x -> sameName(s.hotelOf(x), hc.getHotelName()))
                .filter(x -> hc.getGender() == null || hc.getGender().isBlank() || hc.getGender().equalsIgnoreCase(x.getGender() != null ? x.getGender() : ""))
                .sorted(Comparator.comparing(x -> x.getFullName() == null ? "" : x.getFullName().toLowerCase()))
                .toList();
            for (Participant x : staying) {
                RoomAssignment r = s.roomOf(x);
                people.add(person(x.getFullName(), x.getPhone(), x.getEmail(), r != null ? r.getRoomLabel() : null));
            }
            String who = "male".equalsIgnoreCase(hc.getGender()) ? " (men)" : "female".equalsIgnoreCase(hc.getGender()) ? " (women)" : "";
            groups.add(group("hotel", hc.getHotelName() + who, people));
        }
        return groups.isEmpty() ? null : groups;
    }

    private static Map<String, Object> group(String kind, String title, List<Map<String, Object>> people) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("title", title);
        m.put("people", people);
        return m;
    }
}
