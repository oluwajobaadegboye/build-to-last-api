package com.btl.transport.admin;

import com.btl.transport.driver.DriverRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.registration.RegistrationFieldRepository;
import com.btl.transport.room.RoomAssignmentRepository;
import com.btl.transport.souvenir.SouvenirItemRepository;
import com.btl.transport.vehicle.VehicleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;
import java.util.Set;

/**
 * Records every successful admin change (POST/PUT/PATCH/DELETE) in admin_audit_log, in the same request —
 * no extra calls from the browser. Request bodies are never stored (they can hold passwords).
 * The action, category and target come from {@link AuditCatalog}; controllers can refine them with {@link Audit}.
 * The target's name is looked up before the change runs, so it is kept even when the change deletes it.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SKIP = Set.of("/api/v1/admin/login", "/api/v1/admin/change-password");
    private static final String NAME = "btl.audit.targetName";

    private final AuditLogRepository repository;
    private final ParticipantRepository participants;
    private final RegistrationFieldRepository questions;
    private final SouvenirItemRepository souvenirs;
    private final AdminUserRepository admins;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final RoomAssignmentRepository rooms;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!MUTATIONS.contains(req.getMethod()) || SKIP.contains(req.getRequestURI())) return true;
        try {
            AuditCatalog.Match m = AuditCatalog.match(req.getMethod(), req.getRequestURI());
            String name = nameOf(m.kind(), m.target());
            if (name != null) req.setAttribute(NAME, name);
        } catch (Exception e) {
            log.debug("Audit name lookup failed for {}", req.getRequestURI(), e);  // never block the change
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String method = req.getMethod();
        String path = req.getRequestURI();
        if (!MUTATIONS.contains(method) || res.getStatus() >= 400 || ex != null || SKIP.contains(path)) return;
        Object user = req.getAttribute(AdminAccess.ATTR_USERNAME);
        if (user == null) return;
        try {
            AuditCatalog.Match m = AuditCatalog.match(method, path);
            String action = attr(req, Audit.ACTION);
            String subject = attr(req, Audit.SUBJECT);
            String name = subject != null ? subject : attr(req, NAME);
            repository.save(AuditLog.builder()
                .programId((String) req.getAttribute(AdminAccess.ATTR_PROGRAM_ID))
                .username(String.valueOf(user))
                .action(cut(action != null ? action : m.action(), 160))
                .category(m.category())
                .target(cut(m.target(), 160))
                .targetName(cut(name, 200))
                .detail(cut(attr(req, Audit.DETAIL), 300))
                .method(method)
                .path(cut(path, 300))
                .build());
        } catch (Exception e) {
            log.warn("Audit log write failed for {} {}", method, path, e);  // never fail the user's change
        }
    }

    /** Name of the thing a path points at, or null. */
    String nameOf(AuditCatalog.Kind kind, String target) {
        if (target == null) return null;
        return switch (kind) {
            case PARTICIPANT_CODE -> participants.findByBtlCode(target).map(Participant::getFullName).orElse(null);
            case PARTICIPANT_ID -> id(target).flatMap(participants::findById).map(Participant::getFullName).orElse(null);
            case QUESTION -> id(target).flatMap(questions::findById).map(q -> q.getLabel()).orElse(null);
            case SOUVENIR -> id(target).flatMap(souvenirs::findById).map(s -> s.getName()).orElse(null);
            case ADMIN -> id(target).flatMap(admins::findById).map(a -> a.getUsername()).orElse(null);
            case DRIVER -> id(target).flatMap(drivers::findById).map(d -> d.getName()).orElse(null);
            case VEHICLE -> id(target).flatMap(vehicles::findById).map(v -> v.getLabel()).orElse(null);
            case ROOM -> id(target).flatMap(rooms::findById)
                .map(r -> r.getRoomLabel() != null ? "room " + r.getRoomLabel() : null).orElse(null);
            case TRIP -> "trip #" + target;
            case NONE -> null;
        };
    }

    private static Optional<Integer> id(String s) {
        try { return Optional.of(Integer.valueOf(s)); } catch (NumberFormatException e) { return Optional.empty(); }
    }

    private static String attr(HttpServletRequest req, String key) {
        Object v = req.getAttribute(key);
        return v == null ? null : v.toString();
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
