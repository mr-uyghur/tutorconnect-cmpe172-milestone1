package edu.sjsu.tutorconnect.service;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One booking attempt in one transaction. Kept in its own bean so BookingService can retry by calling it
 * through the Spring proxy: each retry then gets a brand-new transaction instead of reusing a failed one.
 */
@Service
public class BookingTransaction {
 private final SlotRepository slots;
 private final AppointmentRepository appointments;
 private final Clock clock;
 public BookingTransaction(SlotRepository slots, AppointmentRepository appointments, Clock clock) {
  this.slots = slots; this.appointments = appointments; this.clock = clock;
 }

 /**
  * SQLite uses SERIALIZABLE transactions. The version update and partial unique index
  * protect the slot even when multiple requests read it at the same time.
  * Returns the new appointment id.
  */
 @Transactional(isolation = Isolation.SERIALIZABLE)
 public long bookOnce(long customerId, long slotId) {
  var slot = slots.findRow(slotId).orElseThrow(() -> new NotFoundException("Slot " + slotId + " does not exist."));
  if (!slot.startsAt().isAfter(LocalDateTime.now(clock))) throw new BadRequestException("That session has already started.");
  if (appointments.existsBooked(slotId)) throw new SlotConflictException("This slot was just booked. Please choose another slot.");
  // Compare-and-set on the version read above: only one concurrent transaction can win this update.
  if (slots.bumpVersion(slotId, slot.version()) == 0) throw new VersionConflictException();
  try {
   return appointments.insertBooked(customerId, slotId);
  } catch (DuplicateKeyException e) {
   throw new SlotConflictException("This slot was just booked. Please choose another slot.");
  }
 }
}
