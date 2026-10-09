package edu.sjsu.tutorconnect.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import edu.sjsu.tutorconnect.dto.SlotRow;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class BookingTransactionTest {
 static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);
 static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
 SlotRepository slots = mock(SlotRepository.class);
 AppointmentRepository appts = mock(AppointmentRepository.class);
 BookingTransaction tx = new BookingTransaction(slots, appts, CLOCK);

 SlotRow slot(LocalDateTime start, int version) { return new SlotRow(7, 1, 1, start, start.plusHours(1), version); }

 @Test void booksFutureFreeSlot() {
  when(slots.findRow(7)).thenReturn(Optional.of(slot(NOW.plusDays(1), 3)));
  when(appts.existsBooked(7)).thenReturn(false);
  when(slots.bumpVersion(7, 3)).thenReturn(1);
  when(appts.insertBooked(5, 7)).thenReturn(42L);
  assertEquals(42L, tx.bookOnce(5, 7));
 }
 @Test void unknownSlotIsNotFound() {
  when(slots.findRow(anyLong())).thenReturn(Optional.empty());
  assertThrows(NotFoundException.class, () -> tx.bookOnce(5, 7));
 }
 @Test void slotThatAlreadyStartedIsRejected() {
  when(slots.findRow(7)).thenReturn(Optional.of(slot(NOW.minusMinutes(1), 0)));
  assertThrows(BadRequestException.class, () -> tx.bookOnce(5, 7));
  verify(appts, never()).insertBooked(anyLong(), anyLong());
 }
 @Test void alreadyBookedSlotIsConflict() {
  when(slots.findRow(7)).thenReturn(Optional.of(slot(NOW.plusDays(1), 0)));
  when(appts.existsBooked(7)).thenReturn(true);
  assertThrows(SlotConflictException.class, () -> tx.bookOnce(5, 7));
  verify(slots, never()).bumpVersion(anyLong(), anyInt());
 }
 @Test void lostVersionCheckIsRetryableConflict() {
  when(slots.findRow(7)).thenReturn(Optional.of(slot(NOW.plusDays(1), 0)));
  when(slots.bumpVersion(7, 0)).thenReturn(0);
  assertThrows(VersionConflictException.class, () -> tx.bookOnce(5, 7));
  verify(appts, never()).insertBooked(anyLong(), anyLong());
 }
 @Test void uniqueConstraintBackstopBecomesConflict() {
  when(slots.findRow(7)).thenReturn(Optional.of(slot(NOW.plusDays(1), 0)));
  when(slots.bumpVersion(7, 0)).thenReturn(1);
  when(appts.insertBooked(5, 7)).thenThrow(new DuplicateKeyException("uq_active_booking"));
  assertThrows(SlotConflictException.class, () -> tx.bookOnce(5, 7));
 }
}
