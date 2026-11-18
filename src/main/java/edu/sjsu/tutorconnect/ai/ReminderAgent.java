package edu.sjsu.tutorconnect.ai;

import edu.sjsu.tutorconnect.dto.NotificationRequest;
import edu.sjsu.tutorconnect.dto.NotificationType;
import edu.sjsu.tutorconnect.notification.NotificationGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Agent 2, the autonomous (proactive) agent. On a schedule, with no user prompt, it finds BOOKED sessions starting
 * within the reminder window that have not been reminded, asks Gemini to write a personalized reminder grounded in
 * the cancellation policy, and sends it through the notification gateway (the same timeout/retry/fallback path as M3).
 * If Gemini fails, a plain template is sent instead so the student still gets a reminder.
 * Each appointment is claimed atomically in appointment_reminders, so overlapping runs never double-send; failed
 * deliveries are retried by later runs up to MAX_ATTEMPTS.
 */
@Service
public class ReminderAgent {
 private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);
 static final int MAX_ATTEMPTS = 3, MAX_MESSAGE = 500, BATCH = 50;
 static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a", Locale.US);
 static final DateTimeFormatter SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
 static final String SYSTEM = """
  You write one short, friendly reminder message for a student's upcoming tutoring session.
  Requirements: plain text, at most 400 characters, no markdown, no placeholders, no links.
  Greet the student by first name, state the subject, tutor, and the date and time exactly as given,
  give ONE concrete preparation tip specific to the subject, and mention the cancellation rule
  from the policy excerpt in a few words. Do not invent any other policy.
  """;

 public record Upcoming(long appointmentId, String studentName, String email, String tutor, String subject, LocalDateTime startsAt) {}
 public record Sent(long appointmentId, String recipient, String source, boolean delivered, String message) {}
 public record RunResult(int candidates, int sent, int failed, List<Sent> details) {}

 private final JdbcTemplate jdbc;
 private final ChatClient chat;
 private final NotificationGateway gateway;
 private final PolicyKnowledgeBase knowledge;
 private final Clock clock;
 private final Duration window;
 /** Fair lock: a waiting scheduled run is served before later manual triggers (both paths use the fair queue). */
 private final ReentrantLock running = new ReentrantLock(true);
 /** Longest the scheduled run waits for a manual run to finish before skipping to the next hour. */
 static final Duration SCHEDULE_WAIT = Duration.ofMinutes(5);

 public ReminderAgent(JdbcTemplate jdbc, ChatClient.Builder builder, NotificationGateway gateway, PolicyKnowledgeBase knowledge,
   Clock clock, @Value("${tutorconnect.agent.reminder.window:24h}") Duration window) {
  this.jdbc = jdbc; this.chat = builder.build(); this.gateway = gateway; this.knowledge = knowledge;
  this.clock = clock; this.window = window;
 }

 @Scheduled(cron = "${tutorconnect.agent.reminder.cron}")
 public void scheduledRun() {
  // The schedule waits (bounded) for a manual run in progress instead of skipping at once, so a tutor's click cannot
  // cancel the hourly run for everyone; a stuck run cannot block the scheduler thread forever either.
  // Waiting is safe: each appointment is claimed atomically, so nothing is sent twice.
  if (!acquire(SCHEDULE_WAIT)) {
   log.warn("Reminder agent scheduled run skipped: another run held the lock for over {}", SCHEDULE_WAIT);
   return;
  }
  RunResult r;
  try { r = pass(null); } finally { running.unlock(); }
  log.info("Reminder agent scheduled run: candidates={} sent={} failed={}", r.candidates(), r.sent(), r.failed());
 }

 /**
  * One pass. providerUserId null = every tutor's sessions (the schedule); otherwise only that tutor's sessions (the
  * manual trigger), so one tutor can never send, or use up the retry attempts of, another tutor's reminders.
  * Runs never overlap inside this instance; a manual trigger that collides with a run in progress returns empty.
  */
 public RunResult run(Long providerUserId) {
  // tryLock(0, ...) honours the fair queue (plain tryLock() would jump ahead of a waiting scheduled run).
  if (!acquire(Duration.ZERO)) {
   log.info("Reminder agent already running; skipped");
   return new RunResult(0, 0, 0, List.of());
  }
  try { return pass(providerUserId); } finally { running.unlock(); }
 }

 private boolean acquire(Duration wait) {
  try { return running.tryLock(wait.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS); }
  catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
 }

 private RunResult pass(Long providerUserId) {
  var due = findDue(providerUserId);
  var details = new ArrayList<Sent>();
  String policy = cancellationPolicy();
  for (var u : due) {
   if (!claim(u.appointmentId())) continue; // another run has it, or it is done
   details.add(remind(u, policy));
  }
  int sent = (int) details.stream().filter(Sent::delivered).count();
  return new RunResult(due.size(), sent, details.size() - sent, details);
 }

 List<Upcoming> findDue(Long providerUserId) {
  var now = LocalDateTime.now(clock);
  return jdbc.query("""
   SELECT a.appointment_id, c.full_name, c.email, pu.full_name AS tutor, v.name AS subject, s.starts_at
   FROM appointments a
   JOIN users c ON c.user_id=a.customer_id
   JOIN availability_slots s ON s.slot_id=a.slot_id
   JOIN providers p ON p.provider_id=s.provider_id
   JOIN users pu ON pu.user_id=p.user_id
   JOIN services v ON v.service_id=s.service_id
   LEFT JOIN appointment_reminders r ON r.appointment_id=a.appointment_id
   WHERE a.status='BOOKED' AND s.starts_at > ? AND s.starts_at <= ?
   AND (r.appointment_id IS NULL OR (r.status='FAILED' AND r.attempts < ?))
   AND (? IS NULL OR pu.user_id = ?)
   ORDER BY s.starts_at LIMIT ?""",
   (rs, n) -> new Upcoming(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
    rs.getTimestamp(6).toLocalDateTime()),
   now.format(SQL), now.plus(window).format(SQL), MAX_ATTEMPTS, providerUserId, providerUserId, BATCH);
 }

 /** Atomic claim: a new row, or a FAILED row under the attempt limit, becomes SENDING. Returns false if not claimed. */
 boolean claim(long appointmentId) {
  return jdbc.update("""
   INSERT INTO appointment_reminders(appointment_id,status,attempts) VALUES (?, 'SENDING', 1)
   ON CONFLICT(appointment_id) DO UPDATE SET status='SENDING', attempts=attempts+1, updated_at=CURRENT_TIMESTAMP
   WHERE status='FAILED' AND attempts < ?""", appointmentId, MAX_ATTEMPTS) == 1;
 }

 Sent remind(Upcoming u, String policy) {
  String source = "LLM";
  String message;
  try {
   message = chat.prompt()
    .system(SYSTEM)
    .user("Student: " + u.studentName() + "\nSubject: " + u.subject() + "\nTutor: " + u.tutor()
     + "\nWhen: " + u.startsAt().format(WHEN) + "\nPolicy excerpt: " + policy)
    .call().content();
   if (message == null || message.isBlank()) throw new IllegalStateException("empty reply");
  } catch (RuntimeException e) {
   log.warn("Reminder LLM call failed for appointment {}; using template: {}", u.appointmentId(), e.toString());
   source = "TEMPLATE";
   message = template(u);
  }
  message = fit(message.strip());
  boolean delivered = gateway.send(new NotificationRequest("appointment-" + u.appointmentId() + "-REMINDER", u.email(),
   NotificationType.APPOINTMENT_REMINDER, message, u.appointmentId()));
  jdbc.update("UPDATE appointment_reminders SET status=?, source=?, message=?, updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",
   delivered ? "SENT" : "FAILED", source, message, u.appointmentId());
  log.info("Reminder appointmentId={} source={} delivered={}", u.appointmentId(), source, delivered);
  return new Sent(u.appointmentId(), u.email(), source, delivered, message);
 }

 /** Grounds the reminder in the knowledge base; a retrieval failure must not stop reminders. */
 String cancellationPolicy() {
  try {
   return knowledge.search("cancellation policy notice").stream().findFirst().map(d -> d.getText()).orElse(DEFAULT_POLICY);
  } catch (RuntimeException e) {
   log.warn("Policy retrieval failed; using default policy line: {}", e.toString());
   return DEFAULT_POLICY;
  }
 }
 static final String DEFAULT_POLICY = "You can cancel for free any time before the session starts; please give 12 hours notice.";

 static String template(Upcoming u) {
  String first = u.studentName().split(" ")[0];
  return "Hi " + first + ", reminder: your " + u.subject() + " session with " + u.tutor() + " is on "
   + u.startsAt().format(WHEN) + ". Bring your questions. Need to cancel? It's free any time before the session starts.";
 }

 /** The notification service accepts at most 500 characters. */
 static String fit(String s) {
  return s.length() <= MAX_MESSAGE ? s : s.substring(0, MAX_MESSAGE - 3).strip() + "...";
 }
}
