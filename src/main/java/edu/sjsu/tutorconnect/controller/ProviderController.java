package edu.sjsu.tutorconnect.controller;
import edu.sjsu.tutorconnect.security.AppUserDetails;
import edu.sjsu.tutorconnect.service.ProviderService;
import edu.sjsu.tutorconnect.service.SchedulingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tutor-only pages; the /provider/** prefix is restricted to the PROVIDER role in SecurityConfig. */
@Controller
@RequestMapping("/provider")
public class ProviderController {
 public record SlotForm(@NotNull Long serviceId, @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
   @NotNull @Min(0) @Max(23) Integer hour) {}

 private final ProviderService providers;
 private final SchedulingService scheduling;
 public ProviderController(ProviderService providers, SchedulingService scheduling) { this.providers = providers; this.scheduling = scheduling; }

 @GetMapping("/slots")
 public String slots(@AuthenticationPrincipal AppUserDetails me, Model m) {
  m.addAttribute("slots", providers.mySlots(me.getId()));
  m.addAttribute("services", scheduling.getHome().services());
  return "provider/slots";
 }

 @PostMapping("/slots")
 public String create(@Valid SlotForm form, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
  providers.createSlot(me.getId(), form.serviceId(), form.date(), form.hour());
  ra.addFlashAttribute("notice", "Slot added.");
  return "redirect:/provider/slots";
 }

 @PostMapping("/slots/{id}/delete")
 public String remove(@PathVariable long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
  providers.removeSlot(me.getId(), id);
  ra.addFlashAttribute("notice", "Slot removed.");
  return "redirect:/provider/slots";
 }

 @GetMapping("/appointments")
 public String appointments(@AuthenticationPrincipal AppUserDetails me, Model m) {
  m.addAttribute("appts", providers.myAppointments(me.getId()));
  return "provider/appointments";
 }
}
