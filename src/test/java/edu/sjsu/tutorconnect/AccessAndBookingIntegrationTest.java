package edu.sjsu.tutorconnect;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AccessAndBookingIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;

 @Test void anonymousUserIsSentToLogin() throws Exception {
  mvc.perform(get("/appointments")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
 }
 @Test void publicPagesNeedNoLogin() throws Exception {
  mvc.perform(get("/")).andExpect(status().isOk());
  mvc.perform(get("/slots")).andExpect(status().isOk());
 }
 @Test void rightPasswordSignsIn() throws Exception {
  mvc.perform(formLogin("maya@example.test", "tutor123")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
 }
 @Test void wrongPasswordIsRejected() throws Exception {
  mvc.perform(formLogin("maya@example.test", "nope")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
 }
 @Test void passwordsAreStoredAsBcrypt() {
  var hashes = jdbc.queryForList("SELECT password_hash FROM users", String.class);
  assertFalse(hashes.isEmpty());
  hashes.forEach(h -> assertTrue(h.startsWith("$2"), "expected a BCrypt hash"));
 }
 private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder formLogin(String u, String p) {
  return post("/login").param("username", u).param("password", p).with(csrf());
 }

 @Test @WithUserDetails("alex@example.test") void providerCannotBook() throws Exception {
  mvc.perform(post("/slots/1/book").with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get("/appointments")).andExpect(status().isForbidden());
 }
 @Test @WithUserDetails("maya@example.test") void customerCannotUseProviderPages() throws Exception {
  mvc.perform(get("/provider/slots")).andExpect(status().isForbidden());
  mvc.perform(post("/provider/slots").param("serviceId", "1").param("date", "2030-01-01").param("hour", "9").with(csrf())).andExpect(status().isForbidden());
 }
 @Test @WithUserDetails("maya@example.test") void postWithoutCsrfTokenIsRejected() throws Exception {
  mvc.perform(post("/slots/1/book")).andExpect(status().isForbidden());
 }

 @Test @WithUserDetails("maya@example.test") void customerBooksThenSecondCustomerGetsConflict() throws Exception {
  mvc.perform(post("/slots/1/book").with(csrf())).andExpect(status().is3xxRedirection());
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1 AND status='BOOKED'", Integer.class));
  // Slot 2 is already booked by Maya in the seed data.
  mvc.perform(post("/slots/2/book").with(csrf())).andExpect(status().isConflict());
 }
 @Test @WithUserDetails("maya@example.test") void bookingUnknownSlotIsNotFound() throws Exception {
  mvc.perform(post("/slots/9999/book").with(csrf())).andExpect(status().isNotFound());
 }
 @Test @WithUserDetails("maya@example.test") void bookingAPastSlotIsRejected() throws Exception {
  mvc.perform(post("/slots/5/book").with(csrf())).andExpect(status().isBadRequest());
 }
 @Test @WithUserDetails("jordan@example.test") void cancellingAnotherCustomersAppointmentIsForbidden() throws Exception {
  mvc.perform(post("/appointments/1/cancel").with(csrf())).andExpect(status().isForbidden());
  assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE appointment_id=1", String.class));
 }
 @Test @WithUserDetails("maya@example.test") void ownerCancelsThenSlotIsOpenAgain() throws Exception {
  mvc.perform(post("/appointments/1/cancel").with(csrf())).andExpect(status().is3xxRedirection());
  mvc.perform(get("/api/slots")).andExpect(jsonPath("$.length()").value(4));
  mvc.perform(post("/appointments/1/cancel").with(csrf())).andExpect(status().isConflict());
 }
 @Test @WithUserDetails("maya@example.test") void pastAppointmentShowsAsCompletedAndCannotBeCancelled() throws Exception {
  mvc.perform(get("/appointments")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("COMPLETED")));
  mvc.perform(post("/appointments/2/cancel").with(csrf())).andExpect(status().isBadRequest());
 }
 @Test @WithUserDetails("maya@example.test") void confirmationIsOwnerOnly() throws Exception {
  mvc.perform(get("/appointments/1/confirmation")).andExpect(status().isOk());
 }
 @Test @WithUserDetails("jordan@example.test") void confirmationOfSomeoneElsesAppointmentIsForbidden() throws Exception {
  mvc.perform(get("/appointments/1/confirmation")).andExpect(status().isForbidden());
 }

 @Test void filtersAndPaginationUseLimitOffset() throws Exception {
  mvc.perform(get("/api/slots").param("providerId", "2")).andExpect(jsonPath("$.length()").value(2));
  mvc.perform(get("/api/slots").param("serviceId", "1")).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/slots").param("size", "1").param("page", "0")).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(1));
  mvc.perform(get("/api/slots").param("size", "1").param("page", "1")).andExpect(jsonPath("$[0].id").value(3));
  mvc.perform(get("/api/slots").param("size", "1").param("page", "9")).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void invalidInputGivesBadRequestWithoutStackTrace() throws Exception {
  mvc.perform(get("/api/slots").param("page", "-1")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/slots").param("size", "5000")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/slots").param("date", "not-a-date")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/slots").param("providerId", "abc")).andExpect(status().isBadRequest())
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
 }
 @Test @WithUserDetails("maya@example.test") void unknownPageIsNotFound() throws Exception {
  mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
 }

 @Test @WithUserDetails("alex@example.test") void providerManagesSlotsAndSeesBookings() throws Exception {
  mvc.perform(post("/provider/slots").param("serviceId", "1").param("date", "2099-01-01").param("hour", "9").with(csrf())).andExpect(status().is3xxRedirection());
  mvc.perform(post("/provider/slots").param("serviceId", "1").param("date", "2099-01-01").param("hour", "9").with(csrf())).andExpect(status().isConflict());
  mvc.perform(post("/provider/slots").param("serviceId", "1").param("date", "2099-01-01").param("hour", "30").with(csrf())).andExpect(status().isBadRequest());
  mvc.perform(post("/provider/slots/2/delete").with(csrf())).andExpect(status().isConflict()); // has a booking
  mvc.perform(post("/provider/slots/3/delete").with(csrf())).andExpect(status().isNotFound()); // belongs to Sam
  mvc.perform(post("/provider/slots/1/delete").with(csrf())).andExpect(status().is3xxRedirection());
  mvc.perform(get("/provider/appointments")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Maya Student")));
 }
}
