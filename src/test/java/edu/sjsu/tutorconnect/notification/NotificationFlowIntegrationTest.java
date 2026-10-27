package edu.sjsu.tutorconnect.notification;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.sjsu.tutorconnect.controller.NotificationServiceController;
import edu.sjsu.tutorconnect.controller.NotificationServiceController.Mode;
import edu.sjsu.tutorconnect.dto.NotificationRequest;
import edu.sjsu.tutorconnect.dto.NotificationType;
import edu.sjsu.tutorconnect.exception.SlotConflictException;
import edu.sjsu.tutorconnect.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.ServerSocket;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real HTTP to the in-app mock notification service on a known port. NOT @Transactional: notifications are sent only
 * after a real commit. The listener is async, so assertions poll with a bounded wait.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT) @AutoConfigureMockMvc
class NotificationFlowIntegrationTest {
 static final int PORT = freePort();
 @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
  r.add("server.port", () -> PORT);
  r.add("notification.base-url", () -> "http://127.0.0.1:" + PORT + "/external/notifications");
 }
 static int freePort() { try (var s = new ServerSocket(0)) { return s.getLocalPort(); } catch (Exception e) { throw new IllegalStateException(e); } }

 @Autowired MockMvc mvc;
 @Autowired BookingService booking;
 @Autowired JdbcTemplate jdbc;
 @Autowired MeterRegistry metrics;
 @Autowired NotificationServiceController mock;
 long slotId;

 @BeforeEach void setUp() {
  mock.setMode(Mode.UP);
  jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (1,1,datetime(date('now','localtime'),'+40 days','+9 hours'),datetime(date('now','localtime'),'+40 days','+10 hours'))");
  slotId = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
 }
 @AfterEach void tearDown() {
  mock.setMode(Mode.UP);
  jdbc.update("DELETE FROM appointments WHERE slot_id=?", slotId);
  jdbc.update("DELETE FROM availability_slots WHERE slot_id=?", slotId);
 }

 static void await(BooleanSupplier cond) throws InterruptedException {
  for (int i = 0; i < 100 && !cond.getAsBoolean(); i++) Thread.sleep(50);
  assertTrue(cond.getAsBoolean(), "condition not met within 5s");
 }
 List<NotificationRequest> received() { return mock.receivedForTest(); }
 double failed() { return metrics.counter("tutorconnect.notifications", "result", "failed").count(); }

 @Test void bookingAndCancelEachSendExactlyOneNotification() throws Exception {
  long id = booking.book(1, slotId).id();
  await(() -> received().stream().anyMatch(n -> n.idempotencyKey().equals("appointment-" + id + "-BOOKED")));
  booking.cancel(1, id);
  await(() -> received().stream().anyMatch(n -> n.idempotencyKey().equals("appointment-" + id + "-CANCELLED")));
  var mine = received().stream().filter(n -> n.appointmentId() != null && n.appointmentId() == id).toList();
  assertEquals(2, mine.size());
  assertTrue(mine.stream().anyMatch(n -> n.type() == NotificationType.BOOKING_CONFIRMED && n.recipient().equals("maya@example.test")));
  assertTrue(mine.stream().anyMatch(n -> n.type() == NotificationType.BOOKING_CANCELLED));
 }

 @Test @WithUserDetails("maya@example.test") void bookingSucceedsWhenNotificationServiceIsDown() throws Exception {
  mock.setMode(Mode.DOWN);
  double before = failed();
  mvc.perform(post("/slots/" + slotId + "/book").with(csrf())).andExpect(status().is3xxRedirection());
  assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE slot_id=?", String.class, slotId));
  await(() -> failed() > before);
 }

 @Test void failedBookingSendsNoNotification() throws Exception {
  int before = received().size();
  // Slot 2 is already booked in the seed data, so this conflicts and rolls back.
  assertThrows(SlotConflictException.class, () -> booking.book(4, 2));
  Thread.sleep(400);
  assertEquals(before, received().size());
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=2 AND status='BOOKED'", Integer.class));
 }
}
