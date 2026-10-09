package edu.sjsu.tutorconnect.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import edu.sjsu.tutorconnect.dto.AppointmentDto;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.AppointmentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.access.AccessDeniedException;

class BookingServiceTest {
 BookingTransaction tx = mock(BookingTransaction.class);
 AppointmentRepository appts = mock(AppointmentRepository.class);
 BookingService service = new BookingService(tx, appts);

 AppointmentDto appt(long id, long customerId, String status) {
  var t = LocalDateTime.now().plusDays(1);
  return new AppointmentDto(id, 7, customerId, "Maya", "Alex", "Java", t, t.plusHours(1), status);
 }

 @Test void retriesAfterLostVersionCheckThenSucceeds() {
  when(tx.bookOnce(5, 7)).thenThrow(new VersionConflictException()).thenReturn(42L);
  when(appts.findById(42)).thenReturn(Optional.of(appt(42, 5, "BOOKED")));
  assertEquals(42, service.book(5, 7).id());
  verify(tx, times(2)).bookOnce(5, 7);
 }
 @Test void retriesOnTransientLockFailure() {
  when(tx.bookOnce(5, 7)).thenThrow(new CannotAcquireLockException("lock timeout")).thenReturn(42L);
  when(appts.findById(42)).thenReturn(Optional.of(appt(42, 5, "BOOKED")));
  assertEquals(42, service.book(5, 7).id());
 }
 @Test void givesUpAsConflictAfterMaxAttempts() {
  when(tx.bookOnce(5, 7)).thenThrow(new VersionConflictException());
  assertThrows(SlotConflictException.class, () -> service.book(5, 7));
  verify(tx, times(BookingService.MAX_ATTEMPTS)).bookOnce(5, 7);
 }
 @Test void conflictIsNeverRetried() {
  when(tx.bookOnce(5, 7)).thenThrow(new SlotConflictException("taken"));
  assertThrows(SlotConflictException.class, () -> service.book(5, 7));
  verify(tx, times(1)).bookOnce(5, 7);
 }

 @Test void ownerCanCancel() {
  when(appts.cancelOwnUpcoming(9, 5)).thenReturn(1);
  assertDoesNotThrow(() -> service.cancel(5, 9));
 }
 @Test void cancellingSomeoneElsesAppointmentIsForbidden() {
  when(appts.cancelOwnUpcoming(9, 6)).thenReturn(0);
  when(appts.findById(9)).thenReturn(Optional.of(appt(9, 5, "BOOKED")));
  assertThrows(AccessDeniedException.class, () -> service.cancel(6, 9));
 }
 @Test void cancellingMissingAppointmentIsNotFound() {
  when(appts.findById(anyLong())).thenReturn(Optional.empty());
  assertThrows(NotFoundException.class, () -> service.cancel(5, 9));
 }
 @Test void cancellingTwiceIsConflict() {
  when(appts.findById(9)).thenReturn(Optional.of(appt(9, 5, "CANCELLED")));
  assertThrows(SlotConflictException.class, () -> service.cancel(5, 9));
 }
 @Test void cancellingAStartedSessionIsRejected() {
  when(appts.findById(9)).thenReturn(Optional.of(appt(9, 5, "BOOKED")));
  assertThrows(BadRequestException.class, () -> service.cancel(5, 9));
 }
 @Test void cancellingACompletedSessionIsRejected() {
  when(appts.findById(9)).thenReturn(Optional.of(appt(9, 5, "COMPLETED")));
  assertThrows(BadRequestException.class, () -> service.cancel(5, 9));
 }
 @Test void myAppointmentsSplitsUpcomingFromHistory() {
  when(appts.findByCustomer(5)).thenReturn(List.of(appt(1, 5, "BOOKED"), appt(2, 5, "COMPLETED"), appt(3, 5, "CANCELLED")));
  var r = service.myAppointments(5);
  assertEquals(1, r.upcoming().size());
  assertEquals(2, r.history().size());
 }
}
