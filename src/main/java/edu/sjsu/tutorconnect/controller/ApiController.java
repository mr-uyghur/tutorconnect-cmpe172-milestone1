package edu.sjsu.tutorconnect.controller;
import edu.sjsu.tutorconnect.dto.*;
import edu.sjsu.tutorconnect.service.SchedulingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** The Milestone 1 JSON reads, now under /api. */
@RestController
@RequestMapping("/api")
@Validated
public class ApiController {
 private final SchedulingService service;
 public ApiController(SchedulingService service) { this.service = service; }

 @GetMapping("/home") public HomeDto home() { return service.getHome(); }

 @GetMapping("/slots")
 public List<SlotDto> slots(@RequestParam(required = false) Long providerId, @RequestParam(required = false) Long serviceId,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
   @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "50") @Min(1) @Max(50) int size) {
  return service.searchSlots(new SlotFilter(providerId, serviceId, date), page, size).items();
 }
}
