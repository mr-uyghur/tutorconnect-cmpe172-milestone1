package edu.sjsu.tutorconnect.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.sjsu.tutorconnect.dto.SlotRow;
import edu.sjsu.tutorconnect.exception.*;
import edu.sjsu.tutorconnect.repository.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ProviderServiceTest {
 static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);
 SlotRepository slots = mock(SlotRepository.class);
 CatalogRepository catalog = mock(CatalogRepository.class);
 UserRepository users = mock(UserRepository.class);
 ProviderService service = new ProviderService(slots, mock(AppointmentRepository.class), catalog, users, CLOCK);
 LocalDate tomorrow = LocalDate.now(CLOCK).plusDays(1);

 ProviderServiceTest() { when(users.findProviderId(2)).thenReturn(Optional.of(10L)); }

 @Test void createsOneHourSlotOnTheHour() {
  when(catalog.serviceExists(1)).thenReturn(true);
  when(slots.insert(10, 1, tomorrow.atTime(9, 0))).thenReturn(55L);
  assertEquals(55L, service.createSlot(2, 1, tomorrow, 9));
 }
 @Test void rejectsInvalidHour() { assertThrows(BadRequestException.class, () -> service.createSlot(2, 1, tomorrow, 24)); }
 @Test void rejectsPastSlot() { assertThrows(BadRequestException.class, () -> service.createSlot(2, 1, tomorrow.minusDays(5), 9)); }
 @Test void rejectsUnknownService() {
  when(catalog.serviceExists(99)).thenReturn(false);
  assertThrows(NotFoundException.class, () -> service.createSlot(2, 99, tomorrow, 9));
 }
 @Test void duplicateStartTimeIsConflict() {
  when(catalog.serviceExists(1)).thenReturn(true);
  when(slots.insert(anyLong(), anyLong(), any())).thenThrow(new DuplicateKeyException("uq_provider_start"));
  assertThrows(SlotConflictException.class, () -> service.createSlot(2, 1, tomorrow, 9));
 }
 @Test void removesOwnUnusedSlot() {
  when(slots.deleteIfUnused(5, 10)).thenReturn(1);
  assertDoesNotThrow(() -> service.removeSlot(2, 5));
 }
 @Test void slotWithHistoryCannotBeRemoved() {
  when(slots.deleteIfUnused(5, 10)).thenReturn(0);
  when(slots.findRow(5)).thenReturn(Optional.of(new SlotRow(5, 10, 1, tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), 1)));
  assertThrows(SlotConflictException.class, () -> service.removeSlot(2, 5));
 }
 @Test void otherProvidersSlotLooksNonexistent() {
  when(slots.deleteIfUnused(5, 10)).thenReturn(0);
  when(slots.findRow(5)).thenReturn(Optional.of(new SlotRow(5, 11, 1, tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), 1)));
  assertThrows(NotFoundException.class, () -> service.removeSlot(2, 5));
 }
}
