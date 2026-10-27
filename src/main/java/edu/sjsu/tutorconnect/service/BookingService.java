package edu.sjsu.tutorconnect.service;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.*;
import edu.sjsu.tutorconnect.notification.AppointmentEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
 private static final Logger log = LoggerFactory.getLogger(BookingService.class);
 static final int MAX_ATTEMPTS = 3;
 private final BookingTransaction tx;
 private final AppointmentRepository appointments;
 private final ApplicationEventPublisher events;
 private final MeterRegistry metrics;
 @Autowired
 public BookingService(BookingTransaction tx, AppointmentRepository appointments, ApplicationEventPublisher events, MeterRegistry metrics) {
  this.tx = tx; this.appointments = appointments; this.events = events; this.metrics = metrics;
 }
 /** Without notifications or shared metrics (unit tests). */
 BookingService(BookingTransaction tx, AppointmentRepository appointments) { this(tx, appointments, e -> {}, new SimpleMeterRegistry()); }

 /**
  * Books a slot, retrying only when the optimistic check or the database lock lost a race. A retry re-reads the
  * slot, so it either succeeds (the other booking was cancelled) or sees the slot taken and fails with a conflict.
  * A duplicate-key conflict is final and is never retried.
  */
 public AppointmentDto book(long customerId, long slotId) {
  var sample = Timer.start(metrics);
  String outcome = "error";
  try {
   var dto = bookWithRetry(customerId, slotId);
   outcome = "success";
   log.info("Booking created appointmentId={} customerId={} slotId={}", dto.id(), customerId, slotId);
   return dto;
  } catch (SlotConflictException e) {
   outcome = "conflict";
   throw e;
  } finally {
   metrics.counter("tutorconnect.bookings", "outcome", outcome).increment();
   sample.stop(metrics.timer("tutorconnect.booking.latency", "outcome", outcome));
  }
 }

 private AppointmentDto bookWithRetry(long customerId, long slotId) {
  for (int attempt = 1; ; attempt++) {
   try {
    long id = tx.bookOnce(customerId, slotId);
    return appointments.findById(id).orElseThrow(() -> new NotFoundException("Appointment " + id + " does not exist."));
   } catch (VersionConflictException | ConcurrencyFailureException e) {
    if (attempt == MAX_ATTEMPTS) throw new SlotConflictException("This slot was just booked. Please choose another slot.");
    log.warn("Booking optimistic-lock retry attempt {}/{} slotId={}", attempt + 1, MAX_ATTEMPTS, slotId);
   }
  }
 }

 /** Cancels the caller's own upcoming appointment; the ownership rule is part of the UPDATE itself. */
 @Transactional
 public void cancel(long customerId, long appointmentId) {
  if (appointments.cancelOwnUpcoming(appointmentId, customerId) == 1) {
   events.publishEvent(new AppointmentEvent(appointmentId, AppointmentEvent.Kind.CANCELLED));
   log.info("Appointment cancelled appointmentId={} customerId={}", appointmentId, customerId);
   return;
  }
  // Nothing was updated: work out why so the caller gets the right status code.
  var a = appointments.findById(appointmentId).orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " does not exist."));
  if (a.customerId() != customerId) throw new AccessDeniedException("Not your appointment.");
  if ("CANCELLED".equals(a.status())) throw new SlotConflictException("This appointment is already cancelled.");
  throw new BadRequestException("A session that has already started cannot be cancelled.");
 }

 public AppointmentDto getOwned(long customerId, long appointmentId) {
  var a = appointments.findById(appointmentId).orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " does not exist."));
  if (a.customerId() != customerId) throw new AccessDeniedException("Not your appointment.");
  return a;
 }

 public MyAppointments myAppointments(long customerId) {
  List<AppointmentDto> all = appointments.findByCustomer(customerId);
  var upcoming = all.stream().filter(a -> "BOOKED".equals(a.status())).sorted(java.util.Comparator.comparing(AppointmentDto::startsAt)).toList();
  var history = all.stream().filter(a -> !"BOOKED".equals(a.status())).toList();
  return new MyAppointments(upcoming, history);
 }
}
