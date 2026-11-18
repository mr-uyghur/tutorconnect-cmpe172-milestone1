package edu.sjsu.tutorconnect.controller;

import edu.sjsu.tutorconnect.ai.ReminderAgent;
import edu.sjsu.tutorconnect.dto.AppointmentDto;
import edu.sjsu.tutorconnect.repository.AppointmentRepository;
import edu.sjsu.tutorconnect.security.AppUserDetails;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for Agent 2 (the scheduled reminder agent), for demos and operations. PROVIDER only.
 * The run covers every due appointment, but the response shows totals plus details (recipient, message) only for the
 * caller's own sessions, so one tutor never sees another tutor's students.
 */
@RestController
public class AgentOpsController {
 private final ReminderAgent reminders;
 private final AppointmentRepository appointments;
 public AgentOpsController(ReminderAgent reminders, AppointmentRepository appointments) {
  this.reminders = reminders; this.appointments = appointments;
 }

 @PostMapping("/provider/agents/reminders/run")
 public ReminderAgent.RunResult runReminders(@AuthenticationPrincipal AppUserDetails me) {
  var result = reminders.run();
  Set<Long> mine = appointments.findByProviderUser(me.getId()).stream().map(AppointmentDto::id).collect(Collectors.toSet());
  return new ReminderAgent.RunResult(result.candidates(), result.sent(), result.failed(),
   result.details().stream().filter(d -> mine.contains(d.appointmentId())).toList());
 }
}
