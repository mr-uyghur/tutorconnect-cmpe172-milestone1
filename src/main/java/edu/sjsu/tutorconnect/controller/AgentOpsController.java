package edu.sjsu.tutorconnect.controller;

import edu.sjsu.tutorconnect.ai.ReminderAgent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for Agent 2 (the scheduled reminder agent), for demos and operations. PROVIDER (staff) only. */
@RestController
public class AgentOpsController {
 private final ReminderAgent reminders;
 public AgentOpsController(ReminderAgent reminders) { this.reminders = reminders; }

 @PostMapping("/provider/agents/reminders/run")
 public ReminderAgent.RunResult runReminders() { return reminders.run(); }
}
