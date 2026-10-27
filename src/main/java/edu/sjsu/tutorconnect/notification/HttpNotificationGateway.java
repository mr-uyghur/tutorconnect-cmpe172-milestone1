package edu.sjsu.tutorconnect.notification;
import edu.sjsu.tutorconnect.dto.NotificationRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/**
 * Calls the notification service over HTTP with connect/read timeouts. Timeouts, IO errors and 5xx are retried with
 * exponential backoff; 4xx (including a rejected API key) is final. When every attempt fails the message is dropped
 * after an ERROR log and a metric: a booking must never fail because of a notification.
 */
@Component
public class HttpNotificationGateway implements NotificationGateway {
 private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);
 private final RestClient client;
 private final int maxAttempts;
 private final Duration backoff;
 private final MeterRegistry metrics;

 @Autowired
 public HttpNotificationGateway(@Value("${notification.base-url}") String baseUrl, @Value("${notification.api-key}") String apiKey,
   @Value("${notification.connect-timeout}") Duration connectTimeout, @Value("${notification.read-timeout}") Duration readTimeout,
   @Value("${notification.max-attempts}") int maxAttempts, @Value("${notification.backoff}") Duration backoff, MeterRegistry metrics) {
  this(RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Notification-Key", apiKey).requestFactory(factory(connectTimeout, readTimeout)).build(),
   maxAttempts, backoff, metrics);
 }

 /** For tests: bring your own client. */
 HttpNotificationGateway(RestClient client, int maxAttempts, Duration backoff, MeterRegistry metrics) {
  this.client = client; this.maxAttempts = maxAttempts; this.backoff = backoff; this.metrics = metrics;
 }

 private static SimpleClientHttpRequestFactory factory(Duration connect, Duration read) {
  var f = new SimpleClientHttpRequestFactory();
  f.setConnectTimeout(connect); f.setReadTimeout(read);
  return f;
 }

 @Override
 public void send(NotificationRequest request) {
  String reason = "unknown";
  for (int attempt = 1; attempt <= maxAttempts; attempt++) {
   try {
    client.post().body(request).retrieve().toBodilessEntity();
    metrics.counter("tutorconnect.notifications", "result", "sent").increment();
    log.info("Notification sent key={} type={} attempt={}", request.idempotencyKey(), request.type(), attempt);
    return;
   } catch (RestClientResponseException e) {
    reason = "HTTP " + e.getStatusCode().value();
    if (!e.getStatusCode().is5xxServerError()) break; // 4xx: retrying the same request cannot help
   } catch (ResourceAccessException e) {
    reason = "I/O error (" + e.getMostSpecificCause().getClass().getSimpleName() + ")";
   } catch (RuntimeException e) {
    reason = e.getClass().getSimpleName();
    break;
   }
   if (attempt < maxAttempts) {
    log.warn("Notification attempt {}/{} failed, retrying key={} reason={}", attempt, maxAttempts, request.idempotencyKey(), reason);
    metrics.counter("tutorconnect.notifications.retries").increment();
    pause(backoff.multipliedBy(1L << (attempt - 1)));
   }
  }
  metrics.counter("tutorconnect.notifications", "result", "failed").increment();
  log.error("Notification gave up, falling back (booking is unaffected) key={} type={} reason={}", request.idempotencyKey(), request.type(), reason);
 }

 private void pause(Duration d) {
  try { Thread.sleep(d); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
 }
}
