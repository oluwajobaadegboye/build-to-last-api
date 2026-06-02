# Claude Code — Project Rules

## Java

- **No fully qualified class names in code.** Always add a proper `import` statement at the top of the file. Never write `new jakarta.persistence.EntityNotFoundException(...)` or `jakarta.servlet.http.HttpServletRequest` inline — import the type and use the simple name.

## Participant-Facing API — PROTECTED

The application is live with real participants. The following backend files are **strictly off-limits** without explicit permission from the user for that specific change:

- `src/main/java/com/btl/transport/participant/ParticipantController.java` — all public participant endpoints (`/register`, `/participant-status`, `/hotels`, `/programs`, `/coordinator-contacts`, `/resend-code`, `/update-flight`)
- `src/main/java/com/btl/transport/participant/ParticipantDtos.java` — response shapes consumed by the participant-facing frontend
- `src/main/java/com/btl/transport/participant/Participant.java` — entity mapped to live participant data
- `src/main/java/com/btl/transport/participant/ParticipantRepository.java` — queries against live data
- `src/main/resources/db/migration/` — any new migration that alters existing columns, renames tables, or drops data must have explicit approval

**Do not modify these files even for what appears to be a safe or minor change.** If a task requires touching participant-facing code as a side effect, stop and ask for explicit permission before proceeding.
