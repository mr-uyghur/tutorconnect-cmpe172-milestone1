package edu.sjsu.tutorconnect.controller;

import edu.sjsu.tutorconnect.ai.ReminderAgent;
import edu.sjsu.tutorconnect.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for Agent 2 (the scheduled reminder agent), for demos and operations. PROVIDER only, and scoped to the
 * caller: it reminds only the signed-in tutor's own students, so the run, its totals and its details never touch
 * another tutor's appointments. The hourly schedule still covers everyone.
 */
@RestController
public class AgentOpsController {
 private final ReminderAgent reminders;
 public AgentOpsController(ReminderAgent reminders) { this.reminders = reminders; }

 @PostMapping("/provider/agents/reminders/run")
 public ReminderAgent.RunResult runReminders(@AuthenticationPrincipal AppUserDetails me) {
  return reminders.run(me.getId());
 }
}
