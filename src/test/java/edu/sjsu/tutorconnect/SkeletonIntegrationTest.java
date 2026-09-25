package edu.sjsu.tutorconnect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class SkeletonIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Test void homeReadsDatabase() throws Exception {
  jdbc.update("UPDATE services SET name='Changed in database' WHERE service_id=1");
  mvc.perform(get("/")).andExpect(status().isOk()).andExpect(jsonPath("$.providers.length()").value(2))
   .andExpect(jsonPath("$.services[0].name").value("Changed in database"));
 }
 @Test void slotsExcludeBookedAndPast() throws Exception {
  mvc.perform(get("/slots")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
   .andExpect(jsonPath("$[0].id").value(1));
  jdbc.update("UPDATE availability_slots SET starts_at=DATEADD('DAY',-1,CURRENT_DATE),ends_at=DATEADD('HOUR',1,DATEADD('DAY',-1,CURRENT_DATE)) WHERE slot_id=1");
  mvc.perform(get("/slots")).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(3));
 }
 @Test void duplicateBookingIsRejected() {
  assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (1,2,'BOOKED')"));
 }
 @Test void cancellationPreservesHistoryAndAllowsRebooking() {
  jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE appointment_id=1");
  jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (1,2,'BOOKED')");
  jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE slot_id=2");
  jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (1,2,'BOOKED')");
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=2",Integer.class));
 }
 @Test void providerCannotOfferSameTimeForDifferentServices() {
  assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) SELECT provider_id,2,starts_at,ends_at FROM availability_slots WHERE slot_id=1"));
 }
 @Test void nonHourlyAndLongSlotsAreRejected() {
  assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE availability_slots SET starts_at=DATEADD('MINUTE',30,starts_at),ends_at=DATEADD('MINUTE',30,ends_at) WHERE slot_id=1"));
  assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE availability_slots SET ends_at=DATEADD('HOUR',1,ends_at) WHERE slot_id=1"));
 }
 @Test void missingCustomerIsRejected() {
  assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (999,1,'BOOKED')"));
 }
}
