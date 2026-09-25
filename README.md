# TutorConnect - CMPE 172 Milestone 1

TutorConnect is a small tutoring appointment project. Students can view tutors,
subjects, and available one-hour sessions. The booking screens are designs for
later milestones; this milestone implements the required database reads.

## Run

Open this folder in an IDE that supports Java and Maven. Select a Java 17-or-newer
JDK, let Maven load the dependencies, and run `TutorConnectApplication.main`.
Then open the two addresses below.

If you prefer a terminal, install Java 17 or newer and Maven. From this folder, run:

```sh
mvn clean verify
java -Duser.timezone=America/Los_Angeles -jar target/tutorconnect-1.0.0.jar
```

Then open these addresses in a browser:

- http://localhost:8080/ - tutors and subjects
- http://localhost:8080/slots - available sessions

Stop the app with Ctrl+C. If port 8080 is busy, add `--server.port=8081` to the
Java command and use port 8081 in the addresses. The first Maven run downloads
dependencies. The database is recreated with sample data each time the app starts.

The sample data has two tutors, two subjects, and four future slots. Slot 2 is
already booked, so `/slots` returns slots 1, 3, and 4.

## Files

- `docs/Milestone1_Report.pdf`: proposal, all 12 planned features, database design,
  request flow, and controller-pattern comparison.
- `docs/ER_Diagram.pdf`: the five database tables and their relationships.
- `docs/Wireframes.pdf`: the four required screen sketches.
- `src/main/java`: the Controller, Service, Repository, and response classes.
- `src/main/resources/schema.sql`: tables and double-booking rule.
- `src/main/resources/seed.sql`: sample tutors, subjects, and slots.
- `src/test`: checks for the two reads and database rules.

Java and Spring Boot handle requests; JDBC runs the SQL. H2 is the included database.
No ORM is used. The database rejects a second active booking of one slot and
prevents a tutor from offering two sessions at the same hour.

To review the report without building the app, open the PDFs in `docs/`.
