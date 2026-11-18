package edu.sjsu.tutorconnect.ai;

import edu.sjsu.tutorconnect.service.BookingService;
import edu.sjsu.tutorconnect.service.SchedulingService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * Agent 1, the conversational (reactive) agent. Each user message runs one ChatClient call with:
 * the system rules, the conversation memory (multi-turn), and the tools bound to the signed-in customer. Gemini decides
 * which tools to call and in what order (multi-step), Spring AI executes them, and the final text is the reply.
 */
@Service
public class BookingAssistant {
 private static final Logger log = LoggerFactory.getLogger(BookingAssistant.class);
 static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.US);
 static final String SYSTEM = """
  You are the TutorConnect booking assistant for the signed-in student {name}.
  The current local date and time is {now}. Weeks run Monday to Sunday. Use ISO dates (yyyy-MM-dd) in tool calls.

  What you can do, always through the tools: list tutors and subjects, find open slots, list the student's own
  appointments, book a slot, cancel one of their bookings, and answer policy questions.

  Rules:
  - Never invent slots, times, ids, or policies. Look them up with the tools.
  - Multi-step requests are fine (for example: find the earliest Java slot this week, then propose booking it).
  - Booking and cancelling are two steps. First call proposeBooking or proposeCancellation and show the student a
    short summary (subject, tutor, date and time) and ask "Shall I book it?" or "Shall I cancel it?". Only call
    confirmBooking or confirmCancellation after the student clearly says yes in their NEXT message. Never confirm in
    the same reply as the proposal.
  - If a slot is taken or a write fails, say so plainly and offer the alternatives the tool returned.
  - If the request is ambiguous (which subject? which day? which appointment?), ask one short clarifying question.
  - For policy questions call searchPolicies and answer only from its excerpts; if they do not cover it, say you
    don't know and suggest support@tutorconnect.example.test.
  - You can only see and change this student's own appointments.
  - Keep replies short and friendly. Show times like "Tue Nov 18, 10:00 AM".
  """;

 private final ChatClient chat;
 private final ChatMemory memory;
 private final PendingActions pending;
 private final SchedulingService scheduling;
 private final BookingService booking;
 private final PolicyKnowledgeBase knowledge;
 private final Clock clock;

 public BookingAssistant(ChatClient.Builder builder, ChatMemory memory, PendingActions pending, SchedulingService scheduling,
   BookingService booking, PolicyKnowledgeBase knowledge, Clock clock) {
  this.chat = builder.build();
  this.memory = memory; this.pending = pending; this.scheduling = scheduling; this.booking = booking;
  this.knowledge = knowledge; this.clock = clock;
 }

 /**
  * One user turn. The memory and pending-action key includes the user id, so one student can never read or continue
  * another student's conversation even if they guess its conversation id.
  */
 public String chat(long customerId, String customerName, String conversationId, String message) {
  String key = customerId + ":" + conversationId;
  long turn = pending.beginTurn(key);
  var tools = new BookingAssistantTools(customerId, key, turn, scheduling, booking, pending, knowledge);
  log.info("Assistant turn customerId={} conversation={} turn={}", customerId, conversationId, turn);
  return chat.prompt()
   .system(s -> s.text(SYSTEM).param("name", customerName).param("now", LocalDateTime.now(clock).format(NOW)))
   .user(message)
   .advisors(MessageChatMemoryAdvisor.builder(memory).build())
   .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, key))
   .tools(tools)
   .call()
   .content();
 }
}
