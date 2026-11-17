# Claude Code — Project Rules

## Live App — Protect Working Features

**The app is live with real participants.** A regression is not a minor inconvenience — it breaks the experience for real people during the conference.

**Do not modify working code as a side effect of another task**, even if it looks like a cleanup or obvious improvement. If the task requires touching shared infrastructure (an entity, a DTO record, a repository query, a service method used by multiple callers), stop and explicitly list what else depends on that code before changing it.

**Before any change that touches shared code:**
1. List every feature/endpoint currently working that depends on the code being changed
2. State specifically what could break for each one
3. If the user gives permission after seeing that list, proceed — but confirm they understood the blast radius

**When the user gives permission for a risky change:**
- Do not proceed silently. Restate in plain language: "This change will affect X, Y, Z — are you sure?"
- If the impact was not clearly spelled out when they gave permission, re-ask before touching the code
- Permission to "fix the bug" is not permission to refactor the surrounding code

---

## Before Calling Any Backend Change Complete

**The code must compile:**
```
./mvnw compile
```
Do not declare a backend change done if this fails. Fix all errors first.

**For any change that adds or modifies an endpoint:**
1. State the exact HTTP method, path, and request body needed to trigger it
2. State what a successful response looks like
3. If the endpoint writes to the DB, explicitly verify the write — do not assume a 200 OK means the data was saved

**For any persistence change (JPA save, update, delete):**
- Prefer direct JPQL `@Modifying @Query` UPDATE/DELETE over entity load → mutate → save for update operations
  - Entity load → mutate → save relies on Hibernate's dirty-check flush, which can silently fail when `jakarta.transaction.Transactional` and `org.springframework.transaction.annotation.Transactional` are mixed across the call chain
  - A `@Modifying` query always issues a SQL statement immediately — no flush timing ambiguity
- After implementing a persistence change, explicitly state what SQL should be executed and confirm it matches intent

**For any new Spring `@Transactional` usage:**
- Use `org.springframework.transaction.annotation.Transactional` consistently — do not mix with `jakarta.transaction.Transactional` in the same call chain
- Do not add `@Transactional` to `@RestController` methods; put transactions on the service layer

**For any change that adds a field to a JPA entity or a response DTO:**

Before writing a single line of code, grep for every method/query that serializes that entity or DTO into a response and list them. Then update ALL of them — not just the one the current task touches.

Checklist:
1. `grep -rn "ClassName\|table_name" src/` — find every repository query that loads the entity
2. For each query: does it `JOIN FETCH` the new relationship? If not, add it (lazy fields silently return null outside a transaction)
3. `grep -rn "toMap\|toDto\|toResponse\|new.*Dto\|new.*Response"` — find every method that maps the entity to a wire format
4. Add the new field to ALL of them before calling the task done
5. Do not ship a partial field — if `pickup_location` is added to `ParticipantController.toRunDto()`, it must also be added to `RunController.runToMap()`, `AdminController.toRunAdminResponse()`, and any other serializer in the same session

---

## Java

- **Package:** everything lives under `org.programhub` (renamed from `com.btl.transport`).
- **No fully qualified class names in code.** Always add a proper `import` statement at the top of the file. Never write `new jakarta.persistence.EntityNotFoundException(...)`, `java.util.regex.Pattern`, `@org.springframework.beans.factory.annotation.Autowired` or `org.programhub.scheduler.JobLock` inline — import the type and use the simple name. This applies to every edit, including quick scripted ones.
  - Only exceptions: enum literals inside JPQL `@Query` strings (JPQL requires the full name), and a real simple-name clash in one file (e.g. two different `Transactional` or `Email` types) — prefer renaming over qualifying.
  - Before finishing, check the files you changed: `grep -nE '(org|java|jakarta)\.[a-z.]+\.[A-Z]' <files> | grep -v import`.

## Participant-Facing API — PROTECTED

The application is live with real participants. The following backend files are **strictly off-limits** without explicit permission from the user for that specific change:

- `src/main/java/org/programhub/participant/ParticipantController.java` — all public participant endpoints (`/register`, `/participant-status`, `/hotels`, `/programs`, `/coordinator-contacts`, `/resend-code`, `/update-flight`)
- `src/main/java/org/programhub/participant/ParticipantDtos.java` — response shapes consumed by the participant-facing frontend
- `src/main/java/org/programhub/participant/Participant.java` — entity mapped to live participant data
- `src/main/java/org/programhub/participant/ParticipantRepository.java` — queries against live data
- `src/main/resources/db/migration/` — any new migration that alters existing columns, renames tables, or drops data must have explicit approval

**Do not modify these files even for what appears to be a safe or minor change.** If a task requires touching participant-facing code as a side effect, stop and ask for explicit permission before proceeding.

---

## How This Platform Fits Together (follow these patterns)

**Many programs, one database.** Every admin endpoint works on one program, sent as the `X-Program-Id` header.
- Load the program's data scoped by that id, and call `adminAccess.requireProgram(entity.getProgramId())` on anything loaded by id (it answers 404 so other programs' ids can't be probed). Never return or change another program's data.
- A new admin endpoint is **full-admins only** unless `AdminPermissions.RULES` gives it to a narrower permission. When adding a permission, add it to `ALL` and `label()`, and to the frontend's `src/lib/permissions.ts`.
- Add every admin POST/PUT/PATCH/DELETE to `AuditCatalog` so it shows in the Activity log.

**Public (attendee) endpoints are reached with a registration or driver code alone.**
- Codes are made only by `RegCodeService` (program letters + 5 random characters, e.g. `BTL-7KQ4M`). Never make guessable codes (counters, ids, timestamps).
- Any new public endpoint that looks someone up by code must be added to `RateLimitFilter.isCodeLookup` (wrong-code limit: 10/hour per device, 100/hour per network).
- `RateLimitFilter`, `JwtAuthFilter`, `AdminPermissions` and `AdminAccess` are security-critical: list the blast radius before changing them.

**Timed jobs.** Production can run two server copies, and every `@Scheduled` method runs on both. Start each job with `jobLock.acquire("job-name", hold)`, where hold is a little under the interval (see `PaymentReminderJob`), unless running twice is harmless.

**Emails.**
- Build every email with `EmailLayout.forProgram(ProgramBranding.of(program))` so it carries the program's name and sender.
- Text written by admins goes through `.richText()` or `.text()`, which escape it. Never concatenate raw HTML.
- Bulk sends run in the background and record each recipient, so a restart resumes without double-sending (pattern: `AnnouncementSender`).
- `SendGridService.sendHtmlEmailChecked` tells you whether the send was accepted.

**Migrations.** Use the next `V<n>__name.sql`; never edit one that has been applied.
- `programs.id` is TEXT and participants' ids are integers.
- New columns are nullable or have a default, so the live data keeps working.
- The JPA entity must match the migration (`ddl-auto: validate` fails startup otherwise).

**Config and secrets.**
- Every new setting in `application.yml` needs a safe default, e.g. `${GOOGLE_SERVICE_ACCOUNT_JSON:}`, so the API starts without it.
- Only add a secret mapping to `infra/ecs.tf` after that key exists in Secrets Manager; a missing key stops the task from starting.

**Checks before calling a change done:**
- `./mvnw -o test` passes.
- For endpoint/DB changes, run the API locally against a fresh database (`DB_URL=jdbc:postgresql://localhost:5432/<new db> ... ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`) so all migrations run from zero, and call the endpoint.

---

## Commit / Push Rules

- Never commit or push without being explicitly asked.
- Never add `Co-Authored-By: Claude` to commit messages.
- Never commit `infra/vpc_endpoints.tf` (local changes). `AGENTS.md` holds the same rules as this file for other coding tools — keep the two in step.
- When a change spans both repos, commit, push and deploy the API first, then the frontend.
