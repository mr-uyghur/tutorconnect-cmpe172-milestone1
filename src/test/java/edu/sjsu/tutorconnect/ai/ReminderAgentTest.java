package edu.sjsu.tutorconnect.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.sjsu.tutorconnect.dto.NotificationRequest;
import edu.sjsu.tutorconnect.dto.NotificationType;
import edu.sjsu.tutorconnect.notification.NotificationGateway;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Agent 2: the scheduled job finds sessions in the next 24h, has the LLM write the reminder, and sends it through the
 * notification gateway exactly once. Assertions look only at this test's own appointments.
 */
@SpringBootTest @AutoConfigureMockMvc @Import(FakeAiConfig.class)
class ReminderAgentTest {
 @Autowired ReminderAgent agent;
 @Autowired FakeAiConfig.FakeChatModel llm;
 @Autowired JdbcTemplate jdbc;
 @MockitoBean NotificationGateway gateway;
 final List<Long> slots = new ArrayList<>();

 @BeforeEach void setUp() {
  llm.reset();
  when(gateway.send(any())).thenReturn(true);
  // These tests own every appointment in the window, so start from a clean window.
  jdbc.update("DELETE FROM appointment_reminders");
 }

 @AfterEach void tearDown() {
  for (long s : slots) {
   jdbc.update("DELETE FROM appointment_reminders WHERE appointment_id IN (SELECT appointment_id FROM appointments WHERE slot_id=?)", s);
   jdbc.update("DELETE FROM appointments WHERE slot_id=?", s);
   jdbc.update("DELETE FROM availability_slots WHERE slot_id=?", s);
  }
 }

 /** Books a new slot for Maya starting `hoursAhead` hours from now (rounded to the hour) and returns the appointment id. */
 long bookIn(int hoursAhead, String status) {
  jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (2,2,"
   + "strftime('%Y-%m-%d %H:00:00', datetime('now','localtime','+" + hoursAhead + " hours')),"
   + "strftime('%Y-%m-%d %H:00:00', datetime('now','localtime','+" + (hoursAhead + 1) + " hours')))");
  long slot = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
  slots.add(slot);
  jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (1,?,?)", slot, status);
  return jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id=?", Long.class, slot);
 }

 List<NotificationRequest> sentFor(long appointmentId) {
  var captor = ArgumentCaptor.forClass(NotificationRequest.class);
  verify(gateway, atLeast(0)).send(captor.capture());
  return captor.getAllValues().stream().filter(r -> r.appointmentId() != null && r.appointmentId() == appointmentId).toList();
 }

 String status(long appointmentId) {
  return jdbc.queryForObject("SELECT status FROM appointment_reminders WHERE appointment_id=?", String.class, appointmentId);
 }

 @Test void sendsOneLlmWrittenReminderThroughTheNotificationGateway() {
  long id = bookIn(5, "BOOKED");
  llm.responder = p -> "Hi Maya, your SQL and Databases session with Sam Rivera is coming up. Tip: bring your schema.";

  var result = agent.run(null);

  var sent = sentFor(id);
  assertEquals(1, sent.size());
  assertEquals(NotificationType.APPOINTMENT_REMINDER, sent.get(0).type());
  assertEquals("appointment-" + id + "-REMINDER", sent.get(0).idempotencyKey());
  assertEquals("maya@example.test", sent.get(0).recipient());
  assertTrue(sent.get(0).message().startsWith("Hi Maya"));
  assertTrue(result.details().stream().anyMatch(d -> d.appointmentId() == id && d.source().equals("LLM") && d.delivered()));
  assertEquals("SENT", status(id));
  // The LLM was given the session facts and the retrieved cancellation policy.
  // (Seed appointments may also be due, so look for this session's prompt among all prompts.)
  boolean grounded = java.util.stream.IntStream.range(0, llm.prompts.size()).mapToObj(llm::text)
   .anyMatch(t -> t.contains("SQL and Databases") && t.contains("Sam Rivera") && t.contains("Policy excerpt") && t.contains("cancel"));
  assertTrue(grounded, "the prompt carries the session facts and the retrieved cancellation policy");
 }

 @Test void aSecondRunDoesNotRemindAgain() {
  long id = bookIn(5, "BOOKED");
  agent.run(null);
  agent.run(null);
  assertEquals(1, sentFor(id).size());
 }

 @Test void skipsCancelledAndFarFutureSessions() {
  long cancelled = bookIn(5, "CANCELLED");
  long later = bookIn(72, "BOOKED");
  agent.run(null);
  assertTrue(sentFor(cancelled).isEmpty());
  assertTrue(sentFor(later).isEmpty());
 }

 @Test void fallsBackToATemplateWhenTheLlmFails() {
  long id = bookIn(5, "BOOKED");
  llm.responder = p -> { throw new RuntimeException("Gemini unavailable"); };
  var result = agent.run(null);
  var sent = sentFor(id);
  assertEquals(1, sent.size());
  assertTrue(sent.get(0).message().startsWith("Hi Maya, reminder:"), sent.get(0).message());
  assertTrue(result.details().stream().anyMatch(d -> d.appointmentId() == id && d.source().equals("TEMPLATE")));
 }

 @Test void failedDeliveryIsRetriedByTheNextRunThenMarkedSent() {
  long id = bookIn(5, "BOOKED");
  when(gateway.send(any())).thenReturn(false);
  agent.run(null);
  assertEquals("FAILED", status(id));
  when(gateway.send(any())).thenReturn(true);
  agent.run(null);
  assertEquals("SENT", status(id));
  assertEquals(2, jdbc.queryForObject("SELECT attempts FROM appointment_reminders WHERE appointment_id=?", Integer.class, id));
 }

 @Test void aTutorsManualRunTouchesOnlyThatTutorsSessions() {
  long samsSession = bookIn(5, "BOOKED"); // bookIn uses provider 2 = Sam Rivera (user 3)
  var alexRun = agent.run(2L);              // Alex Chen (user 2)
  assertTrue(alexRun.details().stream().noneMatch(d -> d.appointmentId() == samsSession));
  assertTrue(sentFor(samsSession).isEmpty());
  assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM appointment_reminders WHERE appointment_id=?", Integer.class, samsSession),
   "Alex's run did not claim or use up attempts on Sam's session");
  agent.run(3L);
  assertEquals(1, sentFor(samsSession).size());
 }

 @Test void scheduledRunWaitsForAManualRunInsteadOfSkipping() throws Exception {
  long id = bookIn(5, "BOOKED");
  var inLlm = new java.util.concurrent.CountDownLatch(1);
  var release = new java.util.concurrent.CountDownLatch(1);
  llm.responder = p -> { inLlm.countDown(); try { release.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } return "Hi Maya"; };
  var manual = new Thread(() -> agent.run(3L));   // Sam's manual run holds the lock inside the LLM call
  manual.start();
  assertTrue(inLlm.await(5, java.util.concurrent.TimeUnit.SECONDS));
  var scheduled = new Thread(agent::scheduledRun);
  scheduled.start();
  Thread.sleep(200);
  assertTrue(scheduled.isAlive(), "the scheduled run is waiting, not skipped");
  llm.responder = p -> "Hi Maya";
  release.countDown();
  manual.join(5000); scheduled.join(5000);
  assertFalse(scheduled.isAlive());
  assertEquals(1, sentFor(id).size(), "still exactly one reminder");
 }

 @Test void longLlmOutputIsCappedForTheNotificationService() {
  long id = bookIn(5, "BOOKED");
  llm.responder = p -> "x".repeat(900);
  agent.run(null);
  assertTrue(sentFor(id).get(0).message().length() <= 500);
 }
}
