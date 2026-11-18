package edu.sjsu.tutorconnect.ai;

import static org.junit.jupiter.api.Assertions.*;

import edu.sjsu.tutorconnect.service.BookingService;
import edu.sjsu.tutorconnect.service.SchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Required M4 test: the agent's book tool goes through the transactional service path, so the double-booking guard
 * (SERIALIZABLE transaction + version check + unique index) still holds when the agent books. The tools are invoked
 * through Spring AI ToolCallbacks with JSON arguments, the same way Gemini's tool calls are executed at runtime.
 * NOT @Transactional: bookings must really commit, like in ConcurrentBookingTest.
 */
@SpringBootTest @AutoConfigureMockMvc @Import(FakeAiConfig.class)
class AgentBookingToolTest {
 static final long MAYA = 1, JORDAN = 4;
 @Autowired SchedulingService scheduling;
 @Autowired BookingService booking;
 @Autowired PendingActions pending;
 @Autowired PolicyKnowledgeBase knowledge;
 @Autowired JdbcTemplate jdbc;
 @Autowired MeterRegistry metrics;
 long slotId;

 @BeforeEach void setUp() {
  jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (1,1,datetime(date('now','localtime'),'+50 days','+9 hours'),datetime(date('now','localtime'),'+50 days','+10 hours'))");
  slotId = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
 }

 @AfterEach void tearDown() {
  jdbc.update("DELETE FROM appointments WHERE slot_id=?", slotId);
  jdbc.update("DELETE FROM availability_slots WHERE slot_id=?", slotId);
 }

 /** A simulated conversation: each say() is a new user message (turn), with fresh tools bound to that customer. */
 class Conversation {
  final long customerId; final String key = UUID.randomUUID().toString();
  ToolCallback[] tools;
  Conversation(long customerId) { this.customerId = customerId; }
  Conversation say() {
   long turn = pending.beginTurn(customerId + ":" + key);
   tools = ToolCallbacks.from(new BookingAssistantTools(customerId, customerId + ":" + key, turn, scheduling, booking, pending, knowledge));
   return this;
  }
  String call(String tool, String json) {
   for (var t : tools) if (t.getToolDefinition().name().equals(tool)) return unquote(t.call(json));
   throw new AssertionError("no tool " + tool);
  }
 }

 /** Spring AI serializes a String tool result as a JSON string; unwrap it to compare text. */
 static String unquote(String json) {
  try { return json.startsWith("\"") ? new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, String.class) : json; }
  catch (Exception e) { throw new IllegalStateException(e); }
 }

 int booked(long slot) { return jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=? AND status='BOOKED'", Integer.class, slot); }
 double conflicts() { return metrics.counter("tutorconnect.bookings", "outcome", "conflict").count(); }

 @Test void agentBookingOfATakenSlotIsBlockedByTheTransactionalGuard() {
  var maya = new Conversation(MAYA);
  assertTrue(maya.say().call("proposeBooking", "{\"slotId\":" + slotId + "}").contains("PENDING CONFIRMATION"));
  // Between the proposal and the confirmation, another student books the same slot through the normal web path.
  booking.book(JORDAN, slotId);
  double before = conflicts();

  String result = maya.say().call("confirmBooking", "{}");

  assertTrue(result.contains("CONFLICT"), result);
  assertTrue(result.contains("Nothing was booked"), result);
  assertEquals(1, booked(slotId), "still exactly one active booking for the slot");
  assertEquals(JORDAN, jdbc.queryForObject("SELECT customer_id FROM appointments WHERE slot_id=? AND status='BOOKED'", Long.class, slotId));
  assertEquals(before + 1, conflicts(), "the conflict was raised by BookingService.book, the transactional path");
 }

 @Test void twoAgentsConfirmingTheSameSlotAtOnceExactlyOneWins() throws Exception {
  var maya = new Conversation(MAYA); var jordan = new Conversation(JORDAN);
  maya.say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
  jordan.say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
  maya.say(); jordan.say();
  var pool = Executors.newFixedThreadPool(2);
  var go = new CountDownLatch(1);
  List<Future<String>> results = new ArrayList<>();
  for (var c : List.of(maya, jordan)) results.add(pool.submit(() -> { go.await(); return c.call("confirmBooking", "{}"); }));
  go.countDown();
  var texts = new ArrayList<String>();
  for (var f : results) texts.add(f.get(30, TimeUnit.SECONDS));
  pool.shutdown();
  assertEquals(1, texts.stream().filter(t -> t.startsWith("BOOKED")).count(), texts.toString());
  assertEquals(1, texts.stream().filter(t -> t.startsWith("CONFLICT")).count(), texts.toString());
  assertEquals(1, booked(slotId));
 }

 @Test void confirmInTheSameTurnAsTheProposalIsRefused() {
  var maya = new Conversation(MAYA).say();
  maya.call("proposeBooking", "{\"slotId\":" + slotId + "}");
  String result = maya.call("confirmBooking", "{}");
  assertTrue(result.startsWith("REFUSED"), result);
  assertEquals(0, booked(slotId), "no auto-booking on the first turn");
  // The proposal survives; the student's next message can confirm it.
  assertTrue(maya.say().call("confirmBooking", "{}").startsWith("BOOKED"));
  assertEquals(1, booked(slotId));
 }

 @Test void reProposingTheSameSlotBeforeConfirmingStillBooks() {
  var maya = new Conversation(MAYA);
  maya.say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
  // On "yes", the model re-checks the slot and then confirms in the same reply.
  maya.say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
  assertTrue(maya.call("confirmBooking", "{}").startsWith("BOOKED"));
  assertEquals(1, booked(slotId));
 }

 @Test void proposingADifferentSlotNeedsANewConfirmation() {
  jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (1,1,datetime(date('now','localtime'),'+50 days','+11 hours'),datetime(date('now','localtime'),'+50 days','+12 hours'))");
  long other = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
  try {
   var maya = new Conversation(MAYA);
   maya.say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
   maya.say().call("proposeBooking", "{\"slotId\":" + other + "}");
   assertTrue(maya.call("confirmBooking", "{}").startsWith("REFUSED"), "a new summary must be confirmed in a new message");
   assertEquals(0, booked(slotId) + booked(other));
  } finally {
   jdbc.update("DELETE FROM appointments WHERE slot_id=?", other);
   jdbc.update("DELETE FROM availability_slots WHERE slot_id=?", other);
  }
 }

 @Test void confirmWithoutAProposalIsRefused() {
  assertTrue(new Conversation(MAYA).say().call("confirmBooking", "{}").startsWith("REFUSED"));
  assertEquals(0, booked(slotId));
 }

 @Test void proposingATakenSlotOffersAlternatives() {
  booking.book(JORDAN, slotId);
  String result = new Conversation(MAYA).say().call("proposeBooking", "{\"slotId\":" + slotId + "}");
  assertTrue(result.startsWith("UNAVAILABLE"), result);
  assertTrue(result.contains("Alternatives") || result.contains("no other open slots"), result);
 }

 @Test void agentCancelsOnlyTheStudentsOwnBookingAfterConfirmation() {
  long jordansAppointment = booking.book(JORDAN, slotId).id();
  // Maya's agent cannot even propose cancelling Jordan's appointment.
  String other = new Conversation(MAYA).say().call("proposeCancellation", "{\"appointmentId\":" + jordansAppointment + "}");
  assertTrue(other.startsWith("NOT FOUND"), other);
  assertEquals(1, booked(slotId));
  // Jordan's agent can, in two turns.
  var jordan = new Conversation(JORDAN);
  assertTrue(jordan.say().call("proposeCancellation", "{\"appointmentId\":" + jordansAppointment + "}").contains("PENDING CONFIRMATION"));
  assertEquals(1, booked(slotId));
  assertTrue(jordan.say().call("confirmCancellation", "{}").startsWith("CANCELLED"));
  assertEquals(0, booked(slotId));
 }

 @Test void findOpenSlotsFiltersBySubjectAndDateAndHidesBookedSlots() {
  String day = jdbc.queryForObject("SELECT date(starts_at) FROM availability_slots WHERE slot_id=?", String.class, slotId);
  var maya = new Conversation(MAYA).say();
  String json = maya.call("findOpenSlots", "{\"fromDate\":\"" + day + "\",\"toDate\":\"" + day + "\",\"subject\":\"java\"}");
  assertTrue(json.contains("\"slotId\":" + slotId), json);
  assertFalse(maya.call("findOpenSlots", "{\"fromDate\":\"" + day + "\",\"toDate\":\"" + day + "\",\"subject\":\"sql\"}").contains("\"slotId\":" + slotId));
  booking.book(JORDAN, slotId);
  assertFalse(maya.call("findOpenSlots", "{\"fromDate\":\"" + day + "\",\"toDate\":\"" + day + "\"}").contains("\"slotId\":" + slotId));
 }
}
