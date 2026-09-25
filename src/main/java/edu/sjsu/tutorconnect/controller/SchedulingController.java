package edu.sjsu.tutorconnect.controller;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.service.SchedulingService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
public class SchedulingController {
 private final SchedulingService service;
 public SchedulingController(SchedulingService service) { this.service=service; }
 // Video cue: "These are the only two working addresses in Milestone 1.
 // The controller receives a request and asks the service for the result."
 @GetMapping("/") public HomeDto home() { return service.getHome(); }
 @GetMapping("/slots") public List<SlotDto> slots() { return service.getAvailableSlots(); }
}
