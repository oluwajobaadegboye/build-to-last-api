package com.btl.transport.config;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.admin.AdminProperties;
import com.btl.transport.admin.AdminUser;
import com.btl.transport.admin.AdminUserRepository;
import com.btl.transport.admin.JwtService;
import com.btl.transport.admin.ProgramAdminRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/v1/admin";

    /** Endpoints a DB admin may call without acting on a specific program. */
    private static final Set<String> PROGRAM_AGNOSTIC = Set.of(
        PREFIX + "/programs",
        PREFIX + "/change-password",
        PREFIX + "/upload",
        PREFIX + "/me"
    );

    /** /api/v1/admin/programs/{id}[/...] — the program is addressed in the path. */
    private static final Pattern PROGRAM_PATH = Pattern.compile("^" + PREFIX + "/programs/([^/]+)(/.*)?$");

    private final JwtService jwtService;
    private final AdminProperties adminProperties;
    private final AdminUserRepository adminUserRepository;
    private final ProgramAdminRepository programAdminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(PREFIX + "/") || path.equals(PREFIX + "/login")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        String username;
        try {
            username = jwtService.validateAndExtractUsername(token);
        } catch (JwtException e) {
            reject(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        AdminUser dbUser = username == null ? null
            : adminUserRepository.findByUsernameIgnoreCase(username).orElse(null);

        // Super-admins come from YAML config and have no DB row; they can act on any program.
        if (dbUser == null) {
            boolean isYamlAdmin = username != null && adminProperties.getUsers().stream()
                .anyMatch(u -> username.equalsIgnoreCase(u.getUsername()));
            if (!isYamlAdmin) {
                reject(response, HttpStatus.UNAUTHORIZED, "Account no longer exists");
                return;
            }
            request.setAttribute(AdminAccess.ATTR_SUPER, Boolean.TRUE);
            String header = request.getHeader("X-Program-Id");
            if (header != null && !header.isBlank()) request.setAttribute(AdminAccess.ATTR_PROGRAM_ID, header);
            filterChain.doFilter(request, response);
            return;
        }

        request.setAttribute(AdminAccess.ATTR_USER_ID, dbUser.getId());

        if (PROGRAM_AGNOSTIC.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Which program is this request acting on? Path wins over header for /programs/{id}.
        String programId;
        Matcher m = PROGRAM_PATH.matcher(path);
        if (m.matches()) {
            programId = m.group(1);
        } else {
            programId = request.getHeader("X-Program-Id");
        }

        if (programId == null || programId.isBlank()) {
            reject(response, HttpStatus.FORBIDDEN, "No program selected");
            return;
        }
        if (!programAdminRepository.existsByAdminUserIdAndProgramId(dbUser.getId(), programId)) {
            reject(response, HttpStatus.FORBIDDEN, "You do not have access to this program");
            return;
        }
        // For /programs/{id} paths, scope the request to that program even if the header differs.
        request.setAttribute(AdminAccess.ATTR_PROGRAM_ID, programId);

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
