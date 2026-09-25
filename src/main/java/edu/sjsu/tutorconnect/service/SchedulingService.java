package edu.sjsu.tutorconnect.service;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.repository.*;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class SchedulingService {
 private final CatalogRepository catalog;
 private final SlotRepository slots;
 public SchedulingService(CatalogRepository catalog, SlotRepository slots) { this.catalog=catalog; this.slots=slots; }
 // Video cue: "This middle layer decides which database reads each address needs.
 // It keeps the controller separate from the SQL code."
 public HomeDto getHome() { return new HomeDto("TutorConnect",catalog.findProviders(),catalog.findServices()); }
 public List<SlotDto> getAvailableSlots() { return slots.findAvailable(); }
}
