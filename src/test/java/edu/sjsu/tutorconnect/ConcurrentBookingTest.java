package edu.sjsu.tutorconnect;
import static org.junit.jupiter.api.Assertions.*;

import edu.sjsu.tutorconnect.exception.SlotConflictException;
import edu.sjsu.tutorconnect.service.BookingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deliberately NOT @Transactional: the threads must commit real transactions against the shared database. */
@SpringBootTest @AutoConfigureMockMvc // same context configuration as the other tests
class ConcurrentBookingTest {
 @Autowired BookingService booking;
 @Autowired JdbcTemplate jdbc;
 long slotId;
 final List<Long> customers = new ArrayList<>();

 @BeforeEach void setUp() {
  jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (1,1,datetime(date('now','localtime'),'+30 days','+9 hours'),datetime(date('now','localtime'),'+30 days','+10 hours'))");
  slotId = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
  for (int i = 0; i < 10; i++) {
   jdbc.update("INSERT INTO users(full_name,email,password_hash,role) VALUES (?,?,'$2a$10$emm5VMyzxjPHrBbzHiVbW.QtBsDvcUA1bsXh0OkSmMbhz34H0JgtK','CUSTOMER')", "Racer " + i, "racer" + i + "@example.test");
   customers.add(jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, "racer" + i + "@example.test"));
  }
 }

 @AfterEach void tearDown() {
  jdbc.update("DELETE FROM appointments WHERE slot_id=?", slotId);
  jdbc.update("DELETE FROM availability_slots WHERE slot_id=?", slotId);
  for (long id : customers) jdbc.update("DELETE FROM users WHERE user_id=?", id);
 }

 /** Releases all threads at the same instant and returns {successes, conflicts}. */
 int[] raceBookings(int threads) throws Exception {
  var pool = Executors.newFixedThreadPool(threads);
  var ready = new CountDownLatch(threads);
  var go = new CountDownLatch(1);
  var wins = new AtomicInteger();
  var conflicts = new AtomicInteger();
  List<Future<?>> done = new ArrayList<>();
  for (int i = 0; i < threads; i++) {
   long customer = customers.get(i);
   done.add(pool.submit(() -> {
    ready.countDown();
    go.await();
    try { booking.book(customer, slotId); wins.incrementAndGet(); }
    catch (SlotConflictException e) { conflicts.incrementAndGet(); }
    return null;
   }));
  }
  ready.await();
  go.countDown();
  for (var f : done) f.get(30, TimeUnit.SECONDS); // any unexpected exception type fails the test here
  pool.shutdown();
  return new int[]{wins.get(), conflicts.get()};
 }

 @Test void twoSimultaneousBookingsExactlyOneSucceeds() throws Exception {
  int[] r = raceBookings(2);
  assertEquals(1, r[0], "exactly one booking must win");
  assertEquals(1, r[1], "the other must get a slot conflict");
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=? AND status='BOOKED'", Integer.class, slotId));
 }

 @Test void tenSimultaneousBookingsExactlyOneSucceeds() throws Exception {
  int[] r = raceBookings(10);
  assertEquals(1, r[0]);
  assertEquals(9, r[1]);
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=? AND status='BOOKED'", Integer.class, slotId));
 }
}
