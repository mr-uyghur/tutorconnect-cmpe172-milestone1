package edu.sjsu.tutorconnect.notification;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Commit-then-notify: runs only after the booking/cancel transaction committed (a rollback sends nothing), and on a
 * separate executor so a slow or down notification service never delays the HTTP response.
 */
@Component
public class NotificationListener {
 private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
 private final NotificationGateway gateway;
 private final AppointmentRepository appointments;
 private final UserRepository users;
 public NotificationListener(NotificationGateway gateway, AppointmentRepository appointments, UserRepository users) {
  this.gateway = gateway; this.appointments = appointments; this.users = users;
 }

 @Async("notificationExecutor")
 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 public void on(AppointmentEvent e) {
  try {
   var a = appointments.findById(e.appointmentId()).orElse(null);
   var email = a == null ? null : users.findEmailById(a.customerId()).orElse(null);
   if (email == null) { log.warn("No recipient for appointment {}; notification skipped", e.appointmentId()); return; }
   boolean booked = e.kind() == AppointmentEvent.Kind.BOOKED;
   String what = a.serviceName() + " with " + a.providerName() + " on " + a.startsAt();
   gateway.send(new NotificationRequest("appointment-" + e.appointmentId() + "-" + e.kind(), email,
    booked ? NotificationType.BOOKING_CONFIRMED : NotificationType.BOOKING_CANCELLED,
    booked ? "Your session is confirmed: " + what : "Your session was cancelled: " + what, e.appointmentId()));
  } catch (RuntimeException ex) {
   log.error("Could not prepare notification for appointment {}: {}", e.appointmentId(), ex.toString());
  }
 }
}
