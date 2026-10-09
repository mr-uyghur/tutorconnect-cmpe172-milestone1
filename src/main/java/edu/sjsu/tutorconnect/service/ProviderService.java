package edu.sjsu.tutorconnect.service;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService {
 private final SlotRepository slots;
 private final AppointmentRepository appointments;
 private final CatalogRepository catalog;
 private final UserRepository users;
 private final Clock clock;
 public ProviderService(SlotRepository slots, AppointmentRepository appointments, CatalogRepository catalog, UserRepository users, Clock clock) {
  this.slots = slots; this.appointments = appointments; this.catalog = catalog; this.users = users; this.clock = clock;
 }

 private long providerIdOf(long userId) {
  return users.findProviderId(userId).orElseThrow(() -> new NotFoundException("No provider profile for this account."));
 }

 /** Creates a one-hour slot starting on the given whole hour; end time is computed here, never taken from input. */
 public long createSlot(long userId, long serviceId, LocalDate date, int hour) {
  if (hour < 0 || hour > 23) throw new BadRequestException("Hour must be between 0 and 23.");
  var startsAt = date.atTime(hour, 0);
  if (!startsAt.isAfter(LocalDateTime.now(clock))) throw new BadRequestException("A slot must start in the future.");
  if (!catalog.serviceExists(serviceId)) throw new NotFoundException("Service " + serviceId + " does not exist.");
  try {
   return slots.insert(providerIdOf(userId), serviceId, startsAt);
  } catch (DuplicateKeyException e) {
   throw new SlotConflictException("You already offer a session at that time.");
  }
 }

 @Transactional
 public void removeSlot(long userId, long slotId) {
  long providerId = providerIdOf(userId);
  if (slots.deleteIfUnused(slotId, providerId) == 1) return;
  var row = slots.findRow(slotId);
  if (row.isEmpty() || row.get().providerId() != providerId) throw new NotFoundException("Slot " + slotId + " does not exist.");
  throw new SlotConflictException("This slot has appointment history and cannot be removed.");
 }

 public List<ProviderSlotDto> mySlots(long userId) { return slots.findByProvider(providerIdOf(userId)); }

 public List<AppointmentDto> myAppointments(long userId) { return appointments.findByProviderUser(userId); }
}
