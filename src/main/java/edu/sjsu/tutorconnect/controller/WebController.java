package edu.sjsu.tutorconnect.controller;
import edu.sjsu.tutorconnect.dto.SlotFilter;
import edu.sjsu.tutorconnect.security.AppUserDetails;
import edu.sjsu.tutorconnect.service.BookingService;
import edu.sjsu.tutorconnect.service.SchedulingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Customer-facing pages. Request flow: browser -> controller -> service -> repository -> database, and back to a template. */
@Controller
@Validated
public class WebController {
 private final SchedulingService scheduling;
 private final BookingService booking;
 public WebController(SchedulingService scheduling, BookingService booking) { this.scheduling = scheduling; this.booking = booking; }

 @GetMapping("/")
 public String home(Model m) { m.addAttribute("home", scheduling.getHome()); return "home"; }

 @GetMapping("/login")
 public String login() { return "login"; }

 @GetMapping("/slots")
 public String slots(@RequestParam(required = false) Long providerId, @RequestParam(required = false) Long serviceId,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
   @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size, Model m) {
  m.addAttribute("home", scheduling.getHome());
  m.addAttribute("result", scheduling.searchSlots(new SlotFilter(providerId, serviceId, date), page, size));
  m.addAttribute("providerId", providerId).addAttribute("serviceId", serviceId).addAttribute("date", date);
  return "slots";
 }

 // Review page: the slot (and so the service) is read-only, taken from the database.
 @GetMapping("/slots/{slotId}/book")
 public String bookForm(@PathVariable long slotId, @AuthenticationPrincipal AppUserDetails me, Model m) {
  m.addAttribute("slot", scheduling.getOpenSlot(slotId));
  m.addAttribute("studentName", me.getFullName());
  return "book";
 }

 // Post/redirect/get: a refresh of the confirmation page can never submit a second booking.
 @PostMapping("/slots/{slotId}/book")
 public String book(@PathVariable long slotId, @AuthenticationPrincipal AppUserDetails me) {
  var a = booking.book(me.getId(), slotId);
  return "redirect:/appointments/" + a.id() + "/confirmation";
 }

 @GetMapping("/appointments/{id}/confirmation")
 public String confirmation(@PathVariable long id, @AuthenticationPrincipal AppUserDetails me, Model m) {
  m.addAttribute("appointment", booking.getOwned(me.getId(), id));
  return "confirmation";
 }

 @GetMapping("/appointments")
 public String mine(@AuthenticationPrincipal AppUserDetails me, Model m) {
  m.addAttribute("appts", booking.myAppointments(me.getId()));
  return "appointments";
 }

 @PostMapping("/appointments/{id}/cancel")
 public String cancel(@PathVariable long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
  booking.cancel(me.getId(), id);
  ra.addFlashAttribute("notice", "Appointment cancelled.");
  return "redirect:/appointments";
 }
}
