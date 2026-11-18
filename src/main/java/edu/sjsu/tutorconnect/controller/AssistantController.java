package edu.sjsu.tutorconnect.controller;

import edu.sjsu.tutorconnect.ai.BookingAssistant;
import edu.sjsu.tutorconnect.ai.PolicyQaService;
import edu.sjsu.tutorconnect.exception.AiUnavailableException;
import edu.sjsu.tutorconnect.security.AppUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Milestone 4 JSON endpoints. POST /api/agent/chat is the conversational agent (CUSTOMER only, see SecurityConfig);
 * POST /api/ask is grounded policy Q&A for any signed-in user. Both are POST, so they need the CSRF token.
 */
@RestController
@RequestMapping("/api")
public class AssistantController {
 public record ChatRequest(@Pattern(regexp = "[A-Za-z0-9-]{1,64}") String conversationId, @NotBlank @Size(max = 2000) String message) {}
 public record ChatResponse(String conversationId, String reply) {}
 public record AskRequest(@NotBlank @Size(max = 1000) String question) {}

 private final BookingAssistant assistant;
 private final PolicyQaService qa;
 public AssistantController(BookingAssistant assistant, PolicyQaService qa) { this.assistant = assistant; this.qa = qa; }

 @PostMapping("/agent/chat")
 public ChatResponse chat(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ChatRequest req) {
  String conversationId = req.conversationId() == null ? UUID.randomUUID().toString() : req.conversationId();
  try {
   return new ChatResponse(conversationId, assistant.chat(me.getId(), me.getFullName(), conversationId, req.message()));
  } catch (RuntimeException e) {
   throw new AiUnavailableException("The assistant is unavailable right now. You can still book from the Browse slots page.", e);
  }
 }

 @PostMapping("/ask")
 public PolicyQaService.Answer ask(@Valid @RequestBody AskRequest req) {
  try {
   return qa.ask(req.question());
  } catch (RuntimeException e) {
   throw new AiUnavailableException("Policy Q&A is unavailable right now.", e);
  }
 }
}
