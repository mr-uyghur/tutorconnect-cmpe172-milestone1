package edu.sjsu.tutorconnect.service;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.exception.NotFoundException;
import edu.sjsu.tutorconnect.repository.*;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {
 private final CatalogRepository catalog;
 private final SlotRepository slots;
 public SchedulingService(CatalogRepository catalog, SlotRepository slots) { this.catalog=catalog; this.slots=slots; }

 public HomeDto getHome() { return new HomeDto("TutorConnect",catalog.findProviders(),catalog.findServices()); }

 public Page<SlotDto> searchSlots(SlotFilter filter, int page, int size) { return slots.findAvailable(filter, page, size); }

 public SlotDto getOpenSlot(long slotId) {
  return slots.findOpenDetail(slotId).orElseThrow(() -> new NotFoundException("Slot " + slotId + " is not available."));
 }
}
