package com.btl.transport.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Per-request view of the authenticated admin, populated by JwtAuthFilter.
 * Super-admins (YAML-configured) can access every program; DB users only the programs they are members of.
 */
@Component
public class AdminAccess {

    public static final String ATTR_SUPER      = "btl.admin.super";
    public static final String ATTR_USER_ID    = "btl.admin.userId";
    public static final String ATTR_PROGRAM_ID = "btl.admin.programId";
    public static final String ATTR_PERMISSIONS = "btl.admin.permissions";
    public static final String ATTR_USERNAME    = "btl.admin.username";

    /** Signed-in admin's username (for audit fields), or null. */
    public String currentUsername() {
        HttpServletRequest r = request();
        return r == null ? null : (String) r.getAttribute(ATTR_USERNAME);
    }

    private static HttpServletRequest request() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    public boolean isSuper() {
        HttpServletRequest r = request();
        return r != null && Boolean.TRUE.equals(r.getAttribute(ATTR_SUPER));
    }

    /** DB admin user id, or null for super-admins / unauthenticated contexts. */
    public Integer currentUserId() {
        HttpServletRequest r = request();
        return r == null ? null : (Integer) r.getAttribute(ATTR_USER_ID);
    }

    /** The caller's permissions in the current program; super-admins and unscoped requests count as FULL. */
    @SuppressWarnings("unchecked")
    public java.util.Set<String> permissions() {
        HttpServletRequest r = request();
        if (r == null || isSuper()) return java.util.Set.of(AdminPermissions.FULL);
        Object p = r.getAttribute(ATTR_PERMISSIONS);
        return p instanceof java.util.Set<?> s ? (java.util.Set<String>) s : java.util.Set.of(AdminPermissions.FULL);
    }

    public boolean isFull() { return permissions().contains(AdminPermissions.FULL); }

    /** Program verified by the filter for this request (membership already checked). */
    public String currentProgramId() {
        HttpServletRequest r = request();
        return r == null ? null : (String) r.getAttribute(ATTR_PROGRAM_ID);
    }

    /**
     * Ensures an entity loaded by id belongs to the program the caller is acting on.
     * Responds 404 (not 403) so ids from other programs can't be probed.
     */
    public void requireProgram(String entityProgramId) {
        if (isSuper()) return;
        if (entityProgramId == null || !Objects.equals(entityProgramId, currentProgramId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }
}
