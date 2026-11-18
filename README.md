# TutorConnect - CMPE 172 Milestone 2: Booking & Concurrency

TutorConnect is a tutoring appointment system. Students sign in, find an open one-hour
session, and book it. Tutors publish and remove slots and see who booked them.

Code walkthrough video: https://drive.google.com/file/d/1gKlBK-QHXuc6YweKB-FyWoZ8EiJ4YlaS/view?usp=sharing

## What Milestone 2 implements

- **Web UI** (Thymeleaf + Bootstrap): home, filterable/paginated slots, booking review form,
  confirmation, my appointments, tutor slot management, tutor bookings.
- **Login and roles**: email + password form login with a server-side session. Passwords are
  BCrypt hashes. The role is read from the `users` row over JDBC. Provider-only (`/provider/**`) and
  customer-only (`/slots/{id}/book`, `/appointments/**`) routes return **403** for the wrong role.
- **Features**: browse/filter by tutor, subject and date with SQL `LIMIT/OFFSET`; book; cancel own
  appointment (owner-only); view upcoming and history; tutors create/remove slots and view bookings.
  Statuses are `BOOKED`, `CANCELLED`, and `COMPLETED` (derived for past BOOKED rows).
- **Concurrency**: the booking runs in a SQLite `SERIALIZABLE` transaction using an optimistic version
  check on the slot row (compare-and-set), retried up to 3 times on lost races or temporary database
  locks. A partial unique index on booked appointments is the database-level backstop.
- **Errors**: one `@ControllerAdvice` maps failures to 400/403/404/409 (and 500) with no stack traces.
- **Tests**: service unit tests (Mockito), integration tests (MockMvc + security), and a
  multi-thread test proving exactly one concurrent booking of a slot succeeds.

## Run

Requires JDK 21 and Maven. From the project root:

```sh
mvn clean verify
java -jar target/tutorconnect-1.0.0.jar
```

Open http://localhost:8080/. SQLite creates `tutorconnect.db` in the working directory on first run;
bookings remain there after a restart. To reset the demo data, stop the app and delete that database
file before starting it again. If port 8080 is busy, add `--server.port=8081`. An alternate SQLite
database can be selected with `TUTORCONNECT_DB_URL=jdbc:sqlite:/absolute/path/to/file.db`.
No external database server is needed. Since Milestone 4 the app needs a Gemini API key (`GEMINI_API_KEY`, see
below); without it the app stops at startup with a clear error. The sample accounts below are for local demonstration.

### Demo accounts (password for all: `tutor123`)

| Email | Role |
|---|---|
| maya@example.test | Student (has an upcoming booking and a completed one) |
| jordan@example.test | Student |
| alex@example.test | Tutor (Java) |
| sam@example.test | Tutor (SQL) |

### Routes

| Route | Access |
|---|---|
| `GET /`, `GET /slots`, `GET /login` | public |
| `GET /api/home`, `GET /api/slots` | public JSON (the Milestone 1 reads) |
| `GET/POST /slots/{id}/book`, `GET /appointments/{id}/confirmation`, `GET /appointments`, `POST /appointments/{id}/cancel` | customer |
| `GET/POST /provider/slots`, `POST /provider/slots/{id}/delete`, `GET /provider/appointments` | provider |

## Layout

- `controller`: request handling. `service`: rules and transactions. `repository`: JDBC SQL.
- `config`: security and clock. `security`: JDBC user lookup. `exception`: error types and global handler.
- `src/main/resources/schema.sql`, `seed.sql`: SQLite tables, slot version column, partial unique
  booking index, and sample data.
- `src/test`: unit, integration, and concurrency tests.
- `docs/`: `Milestone2_Report.pdf` (this milestone) and the Milestone 1 documents.

Java and Spring Boot handle requests; JDBC runs hand-written SQL against SQLite. No ORM is used.

## Milestone 3: Docker, health, metrics, CI

**Run with Docker** (no local JDK needed):

```sh
docker compose up --build
```

Open http://localhost:8080/. The SQLite file lives on the named volume `tutorconnect-data` (mounted at
`/data`), so bookings survive `docker compose restart` and rebuilds; `docker compose down -v` resets the demo
data. The container binds `0.0.0.0` (`SERVER_ADDRESS`), runs as a non-root user, and the image has a
`HEALTHCHECK` on `/health` (visible in `docker compose ps`). The compose file passes `NOTIFICATION_API_KEY`
from your environment, defaulting to the dev-only value `dev-only-notification-key`; override it in any real
deployment (`NOTIFICATION_API_KEY=... docker compose up`).

**Ops endpoints** (Spring Boot Actuator, mounted at the root):

| Endpoint | Access | Purpose |
|---|---|---|
| `GET /health` | public | liveness plus database connectivity (used by Docker and load balancers) |
| `GET /metrics`, `GET /metrics/{name}` | provider login required | JVM, HTTP and connection-pool metrics |

```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"SQLite","validationQuery":"isValid()"}},
 "diskSpace":{"status":"UP"},"ping":{"status":"UP"},"ssl":{"status":"UP"}}}
```

`/health` runs with `show-details=always` because the milestone requires the database check to be visible.
The tradeoff: anyone can see component status, the database product name and disk figures; no credentials or
application data are included. Metrics are restricted to the provider role.

**Notifications**: booking events are sent to a notification service through a gateway with timeouts and
retries; the mock service is part of this same app.

**CI**: `.github/workflows/ci.yml` runs `mvn -B verify` (unit, integration and concurrency tests) on every
push and pull request using JDK 21 with the Maven cache, then a second job runs `docker build` to prove the
image builds. Badge: `![CI](https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg)`.

## Milestone 4: Enterprise AI (Spring AI + Gemini)

**Setup.** Get a free key at https://aistudio.google.com/apikey, then either `export GEMINI_API_KEY=...` or copy
`.env.example` to `.env` (gitignored) and fill it in. Only the `api-key` property is set; `project-id` / `location`
are deliberately not set, because they switch Spring AI to Vertex AI mode, which rejects an AI Studio key. Models
are configurable: `GEMINI_CHAT_MODEL` (default `gemini-2.5-flash`), `GEMINI_EMBEDDING_MODEL` (default
`gemini-embedding-001`). Spring AI 1.1.8 (`spring-ai-starter-model-google-genai` + `-embedding`) on Spring Boot 3.5.

```sh
export GEMINI_API_KEY=...            # or put it in .env for docker compose
java -jar target/tutorconnect-1.0.0.jar
```

| Piece | Where | What it does |
|---|---|---|
| Agent 1, conversational | `ai/BookingAssistant`, `ai/BookingAssistantTools`, `POST /api/agent/chat`, page `/assistant` | `ChatClient` + tool calling + per-user chat memory. Tools: list tutors/subjects, find open slots, list my appointments, propose/confirm booking, propose/confirm cancellation, search policies. |
| Guarded writes | `BookingAssistantTools` -> `BookingService.book` / `cancel` | The agent books through the same SERIALIZABLE transaction (version check + partial unique index), so double-booking stays blocked. Tools are bound to the signed-in customer id; the model never supplies a user id. |
| Confirm-before-write | `ai/PendingActions` | `propose*` records a pending action; `confirm*` runs only if it was proposed in an earlier user message. Enforced in code, not only in the prompt. |
| Agent 2, autonomous | `ai/ReminderAgent` (`@Scheduled`, hourly) | Finds BOOKED sessions in the next 24 h, has Gemini write a personalized reminder (grounded in the cancellation policy), sends it via the M3 `NotificationGateway`. One reminder per appointment (`appointment_reminders` table, atomic claim); failed deliveries retried up to 3 times; template fallback if Gemini fails. |
| Manual trigger | `POST /provider/agents/reminders/run` (provider; button on "My bookings") | Runs Agent 2 now and returns the run summary as JSON. |
| RAG | `knowledge/tutorconnect-policies.md`, `ai/PolicyKnowledgeBase`, `ai/PolicyQaService`, `POST /api/ask` | Hand-written policy/FAQ file, one document per section, embedded with Gemini into Spring AI's in-memory `SimpleVectorStore` at startup; answers use only the retrieved sections and return their titles as sources. |

| Route | Access |
|---|---|
| `GET /assistant`, `POST /api/agent/chat` `{conversationId?, message}` | customer (CSRF token required) |
| `POST /api/ask` `{question}` | any signed-in user (CSRF token required) |
| `POST /provider/agents/reminders/run` | provider |

If Gemini is down or slow, the AI endpoints return `503` with a message; normal booking pages keep working.
Tests never call Gemini: `src/test/.../ai/FakeAiConfig` supplies a scripted chat model and a bag-of-words embedding
model. `AgentBookingToolTest` is the required test (agent book tool -> transactional path -> double-booking blocked),
invoking the tools through Spring AI `ToolCallback`s with JSON arguments as the model would.
