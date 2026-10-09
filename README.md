# TutorConnect - CMPE 172 Milestone 2: Booking & Concurrency

TutorConnect is a tutoring appointment system. Students sign in, find an open one-hour
session, and book it. Tutors publish and remove slots and see who booked them.

Code walkthrough video: <ADD SHARED VIDEO LINK BEFORE SUBMISSION>

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
No external database server or secrets are needed. The sample accounts below are for local demonstration.

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
- `docs/`: Milestone 1 documents.

Java and Spring Boot handle requests; JDBC runs hand-written SQL against SQLite. No ORM is used.
