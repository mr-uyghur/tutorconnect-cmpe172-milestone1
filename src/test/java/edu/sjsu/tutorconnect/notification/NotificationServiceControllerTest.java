package edu.sjsu.tutorconnect.notification;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.sjsu.tutorconnect.controller.NotificationServiceController;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class NotificationServiceControllerTest {
 @Autowired MockMvc mvc;
 @Autowired NotificationServiceController mock;

 @AfterEach void reset() { mock.setMode(NotificationServiceController.Mode.UP); }

 String body(String key) { return "{\"idempotencyKey\":\"" + key + "\",\"recipient\":\"a@b.test\",\"type\":\"BOOKING_CONFIRMED\",\"message\":\"hi\",\"appointmentId\":1}"; }
 int stored(String key) throws Exception {
  var json = mvc.perform(get("/external/notifications").header("X-Notification-Key", "test-key")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return json.split("\"idempotencyKey\":\"" + key + "\"", -1).length - 1;
 }

 @Test void sameIdempotencyKeyIsStoredOnce() throws Exception {
  var key = UUID.randomUUID().toString();
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body(key)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body(key)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
  assertEquals(1, stored(key));
 }
 @Test void missingOrWrongKeyIs401AndStoresNothing() throws Exception {
  var key = UUID.randomUUID().toString();
  mvc.perform(post("/external/notifications").contentType(MediaType.APPLICATION_JSON).content(body(key))).andExpect(status().isUnauthorized());
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(body(key))).andExpect(status().isUnauthorized());
  mvc.perform(get("/external/notifications")).andExpect(status().isUnauthorized());
  mvc.perform(get("/external/notifications").header("X-Notification-Key", "wrong")).andExpect(status().isUnauthorized());
  mvc.perform(post("/external/notifications/mode").param("value", "DOWN")).andExpect(status().isUnauthorized());
  assertEquals(0, stored(key));
  // The rejected toggle must not have changed the mode.
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body(key))).andExpect(status().isOk());
 }
 @Test void invalidBodyIs400() throws Exception {
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
   .content("{\"idempotencyKey\":\"\",\"recipient\":\"a@b.test\",\"type\":\"BOOKING_CONFIRMED\",\"message\":\"hi\"}")).andExpect(status().isBadRequest());
 }
 @Test void downModeReturns503() throws Exception {
  mvc.perform(post("/external/notifications/mode").param("value", "DOWN").header("X-Notification-Key", "test-key")).andExpect(status().isOk());
  mvc.perform(post("/external/notifications").header("X-Notification-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID().toString())))
   .andExpect(status().isServiceUnavailable());
 }
}
