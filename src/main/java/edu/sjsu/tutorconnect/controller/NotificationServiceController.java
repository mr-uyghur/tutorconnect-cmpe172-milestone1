package edu.sjsu.tutorconnect.controller;
import edu.sjsu.tutorconnect.dto.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * STAND-IN FOR AN EXTERNAL SYSTEM (an email/SMS provider). It lives in this app only so the demo is self-contained;
 * TutorConnect talks to it through NotificationGateway over HTTP, exactly as it would to a real provider.
 * Every endpoint requires the shared X-Notification-Key header.
 */
@RestController
@RequestMapping("/external/notifications")
public class NotificationServiceController {
 private static final Logger log = LoggerFactory.getLogger(NotificationServiceController.class);
 public enum Mode { UP, DOWN, SLOW }
 static final String KEY_HEADER = "X-Notification-Key";
 private final ConcurrentHashMap<String, NotificationRequest> received = new ConcurrentHashMap<>();
 private final AtomicReference<Mode> mode;
 private final byte[] apiKey;
 private final Duration slowDelay;

 public NotificationServiceController(@Value("${notification.api-key}") String apiKey,
   @Value("${notification.mock.mode:UP}") Mode mode, @Value("${notification.mock.slow-delay:3s}") Duration slowDelay) {
  this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8); this.mode = new AtomicReference<>(mode); this.slowDelay = slowDelay;
 }

 public void setMode(Mode m) { mode.set(m); }

 /** Test hook: what the stand-in has stored. */
 public java.util.List<NotificationRequest> receivedForTest() { return java.util.List.copyOf(received.values()); }

 @PostMapping
 public ResponseEntity<NotificationResponse> receive(@RequestHeader(value = KEY_HEADER, required = false) String key,
   @Valid @RequestBody NotificationRequest req) throws InterruptedException {
  if (!authorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  var m = mode.get();
  if (m == Mode.DOWN) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  if (m == Mode.SLOW) Thread.sleep(slowDelay);
  // putIfAbsent makes a retried delivery harmless: the second one is acknowledged but not stored again.
  boolean fresh = received.putIfAbsent(req.idempotencyKey(), req) == null;
  if (fresh) log.info("Mock notification service received key={} type={} recipient={}", req.idempotencyKey(), req.type(), req.recipient());
  else log.info("Mock notification service ignored duplicate key={}", req.idempotencyKey());
  return ResponseEntity.ok(new NotificationResponse(true, !fresh));
 }

 @GetMapping
 public ResponseEntity<Collection<NotificationRequest>> list(@RequestHeader(value = KEY_HEADER, required = false) String key) {
  if (!authorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  return ResponseEntity.ok(received.values());
 }

 /** Demo toggle: UP, DOWN (503) or SLOW (responds after slowDelay, longer than the client read timeout). */
 @PostMapping("/mode")
 public ResponseEntity<Mode> mode(@RequestHeader(value = KEY_HEADER, required = false) String key, @RequestParam Mode value) {
  if (!authorized(key)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  mode.set(value);
  log.info("Mock notification service mode set to {}", value);
  return ResponseEntity.ok(value);
 }

 private boolean authorized(String key) {
  boolean ok = key != null && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), apiKey);
  if (!ok) log.warn("Mock notification service rejected a request with a missing or invalid API key");
  return ok;
 }
}
