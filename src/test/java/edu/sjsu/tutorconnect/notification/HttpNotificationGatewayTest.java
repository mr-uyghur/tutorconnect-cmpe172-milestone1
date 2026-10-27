package edu.sjsu.tutorconnect.notification;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import edu.sjsu.tutorconnect.dto.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpNotificationGatewayTest {
 static final NotificationRequest REQ = new NotificationRequest("appointment-1-BOOKED", "maya@example.test", NotificationType.BOOKING_CONFIRMED, "hi", 1L);
 SimpleMeterRegistry metrics = new SimpleMeterRegistry();
 MockRestServiceServer server;
 HttpNotificationGateway gateway;

 HttpNotificationGatewayTest() {
  var builder = RestClient.builder().baseUrl("http://notify.test/external/notifications").defaultHeader("X-Notification-Key", "k");
  server = MockRestServiceServer.bindTo(builder).build();
  gateway = new HttpNotificationGateway(builder.build(), 3, Duration.ofMillis(1), metrics);
 }
 double count(String name, String... tags) { return metrics.counter(name, tags).count(); }

 @Test void sendsOnceWithApiKey() {
  server.expect(requestTo("http://notify.test/external/notifications")).andExpect(method(HttpMethod.POST))
   .andExpect(header("X-Notification-Key", "k")).andRespond(withSuccess());
  gateway.send(REQ);
  server.verify();
  assertEquals(1, count("tutorconnect.notifications", "result", "sent"));
 }
 @Test void retriesOn5xxThenSucceeds() {
  server.expect(ExpectedCount.times(2), anything()).andRespond(withServerError());
  server.expect(ExpectedCount.once(), anything()).andRespond(withSuccess());
  assertDoesNotThrow(() -> gateway.send(REQ));
  server.verify();
  assertEquals(2, count("tutorconnect.notifications.retries"));
  assertEquals(1, count("tutorconnect.notifications", "result", "sent"));
 }
 @Test void givesUpAfterMaxAttemptsWithoutThrowing() {
  server.expect(ExpectedCount.times(3), anything()).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
  assertDoesNotThrow(() -> gateway.send(REQ));
  server.verify();
  assertEquals(1, count("tutorconnect.notifications", "result", "failed"));
 }
 @Test void doesNotRetry4xx() {
  server.expect(ExpectedCount.once(), anything()).andRespond(withUnauthorizedRequest());
  assertDoesNotThrow(() -> gateway.send(REQ));
  server.verify();
  assertEquals(1, count("tutorconnect.notifications", "result", "failed"));
  assertEquals(0, count("tutorconnect.notifications.retries"));
 }
 @Test void retriesOnIoError() {
  server.expect(ExpectedCount.times(3), anything()).andRespond(request -> { throw new IOException("connection reset"); });
  assertDoesNotThrow(() -> gateway.send(REQ));
  server.verify();
  assertEquals(1, count("tutorconnect.notifications", "result", "failed"));
 }
}
