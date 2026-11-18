package edu.sjsu.tutorconnect.ai;

import edu.sjsu.tutorconnect.ai.PendingActions.Kind;
import edu.sjsu.tutorconnect.dto.AppointmentDto;
import edu.sjsu.tutorconnect.dto.SlotDto;
import edu.sjsu.tutorconnect.dto.SlotFilter;
import edu.sjsu.tutorconnect.exception.BadRequestException;
import edu.sjsu.tutorconnect.exception.NotFoundException;
import edu.sjsu.tutorconnect.exception.SlotConflictException;
import edu.sjsu.tutorconnect.service.BookingService;
import edu.sjsu.tutorconnect.service.SchedulingService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;

/**
 * The tools Agent 1 can call. One instance is created per chat request and is bound to the signed-in customer, so the
 * model never supplies a user id and cannot act for anyone else. Every read and write goes through the existing service
 * layer: booking uses BookingService.book (the SERIALIZABLE transaction with the version check and unique index), and
 * cancelling uses BookingService.cancel (ownership enforced in the UPDATE). Writes are two-step: propose, then confirm in
 * a later user turn (see PendingActions).
 */
public class BookingAssistantTools {
 private static final Logger log = LoggerFactory.getLogger(BookingAssistantTools.class);
 static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.US);
 static final int MAX_SLOTS = 15;
 static final int SCAN_PAGES = 6, PAGE_SIZE = 50;

 public record SlotView(long slotId, String tutor, String subject, String startsAt, String endsAt) {}
 public record AppointmentView(long appointmentId, String tutor, String subject, String startsAt, String status) {}
 public record PolicyExcerpt(String section, String text) {}

 private final long customerId;
 private final String conversationKey;
 private final long turn;
 private final SchedulingService scheduling;
 private final BookingService booking;
 private final PendingActions pending;
 private final PolicyKnowledgeBase knowledge;

 public BookingAssistantTools(long customerId, String conversationKey, long turn, SchedulingService scheduling,
   BookingService booking, PendingActions pending, PolicyKnowledgeBase knowledge) {
  this.customerId = customerId; this.conversationKey = conversationKey; this.turn = turn;
  this.scheduling = scheduling; this.booking = booking; this.pending = pending; this.knowledge = knowledge;
 }

 @Tool(description = "List the tutors and the subjects (services) TutorConnect offers.")
 public String listTutorsAndSubjects() {
  var home = scheduling.getHome();
  var sb = new StringBuilder("Tutors:\n");
  home.providers().forEach(p -> sb.append("- ").append(p.name()).append(": ").append(p.bio()).append('\n'));
  sb.append("Subjects:\n");
  home.services().forEach(s -> sb.append("- ").append(s.name()).append(": ").append(s.description()).append('\n'));
  return sb.toString();
 }

 @Tool(description = """
   Check availability: find open (bookable, future, not yet booked) one-hour slots, earliest first. All filters are \
   optional. Returns at most 15 slots with their slotId.""")
 public List<SlotView> findOpenSlots(
   @ToolParam(required = false, description = "Earliest date to include, ISO format yyyy-MM-dd") String fromDate,
   @ToolParam(required = false, description = "Latest date to include, ISO format yyyy-MM-dd") String toDate,
   @ToolParam(required = false, description = "Part of the subject name, e.g. 'Java' or 'SQL'") String subject,
   @ToolParam(required = false, description = "Part of the tutor's name, e.g. 'Alex'") String tutor) {
  LocalDate from = parseDate(fromDate), to = parseDate(toDate);
  var out = new ArrayList<SlotView>();
  for (int page = 0; page < SCAN_PAGES && out.size() < MAX_SLOTS; page++) {
   var items = scheduling.searchSlots(new SlotFilter(null, null, null), page, PAGE_SIZE).items();
   for (SlotDto s : items) {
    LocalDate d = s.startsAt().toLocalDate();
    if (from != null && d.isBefore(from)) continue;
    if (to != null && d.isAfter(to)) continue;
    if (!contains(s.serviceName(), subject) || !contains(s.providerName(), tutor)) continue;
    out.add(view(s));
    if (out.size() == MAX_SLOTS) break;
   }
   if (items.size() < PAGE_SIZE) break;
  }
  return out;
 }

 @Tool(description = "List the signed-in student's own appointments: upcoming bookings first, then recent history.")
 public List<AppointmentView> listMyAppointments() {
  var mine = booking.myAppointments(customerId);
  var out = new ArrayList<AppointmentView>();
  mine.upcoming().forEach(a -> out.add(view(a)));
  mine.history().stream().limit(5).forEach(a -> out.add(view(a)));
  return out;
 }

 @Tool(description = """
   Step 1 of booking. Checks that the slot is still open and records it as a pending booking. It does NOT book. \
   After calling this, show the student the summary and ask them to confirm. If the slot is taken, offer the \
   returned alternatives instead.""")
 public String proposeBooking(@ToolParam(description = "slotId from findOpenSlots") long slotId) {
  try {
   var slot = scheduling.getOpenSlot(slotId);
   pending.propose(conversationKey, Kind.BOOK, slotId, slot.serviceName(), turn);
   return "PENDING CONFIRMATION (not booked yet): " + slot.serviceName() + " with " + slot.providerName() + ", "
    + slot.startsAt().format(WHEN) + " to " + slot.endsAt().toLocalTime() + " (slotId " + slotId + ")."
    + " Ask the student to confirm. Call confirmBooking only after they say yes in their next message.";
  } catch (NotFoundException e) {
   return "UNAVAILABLE: slot " + slotId + " is not open (already booked, started, or does not exist). " + alternatives(null);
  }
 }

 @Tool(description = """
   Step 2 of booking. Books the pending slot from proposeBooking. Call ONLY after the student explicitly confirmed \
   in a new message. The server refuses if nothing was proposed in an earlier message.""")
 public String confirmBooking() {
  var p = pending.takeConfirmable(conversationKey, Kind.BOOK, turn);
  if (p.isEmpty()) return refusal(Kind.BOOK);
  long slotId = p.get().targetId();
  try {
   var a = booking.book(customerId, slotId);
   log.info("Agent booked appointmentId={} customerId={} slotId={}", a.id(), customerId, slotId);
   return "BOOKED: appointment " + a.id() + ", " + a.serviceName() + " with " + a.providerName() + " on " + a.startsAt().format(WHEN) + ".";
  } catch (SlotConflictException e) {
   log.info("Agent booking hit a conflict customerId={} slotId={}", customerId, slotId);
   return "CONFLICT: " + e.getMessage() + " Nothing was booked. " + alternatives(p.get().subject());
  } catch (BadRequestException | NotFoundException e) {
   return "FAILED: " + e.getMessage() + " Nothing was booked. " + alternatives(null);
  }
 }

 @Tool(description = """
   Step 1 of cancelling. Checks that the appointment belongs to the student and is upcoming, and records it as a \
   pending cancellation. It does NOT cancel. Show the summary and ask the student to confirm.""")
 public String proposeCancellation(@ToolParam(description = "appointmentId from listMyAppointments") long appointmentId) {
  try {
   var a = booking.getOwned(customerId, appointmentId);
   if (!"BOOKED".equals(a.status())) return "NOT CANCELLABLE: appointment " + appointmentId + " is " + a.status() + ".";
   pending.propose(conversationKey, Kind.CANCEL, appointmentId, a.serviceName(), turn);
   return "PENDING CONFIRMATION (not cancelled yet): " + a.serviceName() + " with " + a.providerName() + " on "
    + a.startsAt().format(WHEN) + " (appointment " + appointmentId + "). Ask the student to confirm.";
  } catch (NotFoundException | AccessDeniedException e) {
   return "NOT FOUND: you have no appointment " + appointmentId + ".";
  }
 }

 @Tool(description = """
   Step 2 of cancelling. Cancels the pending appointment from proposeCancellation. Call ONLY after the student \
   explicitly confirmed in a new message.""")
 public String confirmCancellation() {
  var p = pending.takeConfirmable(conversationKey, Kind.CANCEL, turn);
  if (p.isEmpty()) return refusal(Kind.CANCEL);
  long id = p.get().targetId();
  try {
   booking.cancel(customerId, id);
   log.info("Agent cancelled appointmentId={} customerId={}", id, customerId);
   return "CANCELLED: appointment " + id + ". The slot is open again for other students.";
  } catch (SlotConflictException | BadRequestException | NotFoundException | AccessDeniedException e) {
   return "FAILED: " + e.getMessage() + " Nothing was cancelled.";
  }
 }

 @Tool(description = """
   Search TutorConnect's policies and FAQ (hours, cancellation, no-show, reminders, rescheduling, preparation, \
   privacy). Answer policy questions only from the returned excerpts.""")
 public List<PolicyExcerpt> searchPolicies(@ToolParam(description = "The student's policy question") String question) {
  return knowledge.search(question).stream()
   .map(d -> new PolicyExcerpt(String.valueOf(d.getMetadata().get("section")), d.getText())).toList();
 }

 private String refusal(Kind kind) {
  var current = pending.peek(conversationKey);
  if (current.isPresent() && current.get().kind() == kind && current.get().turn() == turn)
   return "REFUSED: the student has not confirmed yet. Show the summary and wait for their reply.";
  return "REFUSED: there is no pending " + (kind == Kind.BOOK ? "booking" : "cancellation")
   + " to confirm. Call " + (kind == Kind.BOOK ? "proposeBooking" : "proposeCancellation") + " first.";
 }

 /** The next three open slots, in the same subject when one is known. */
 private String alternatives(String subject) {
  var next = findOpenSlots(null, null, subject, null).stream().limit(3).toList();
  if (next.isEmpty()) return "There are no other open slots right now.";
  var sb = new StringBuilder("Alternatives you can offer:");
  next.forEach(s -> sb.append(" [slotId ").append(s.slotId()).append(": ").append(s.subject()).append(" with ")
   .append(s.tutor()).append(", ").append(s.startsAt()).append(']'));
  return sb.toString();
 }

 private static boolean contains(String value, String part) {
  return part == null || part.isBlank() || value.toLowerCase(Locale.ROOT).contains(part.trim().toLowerCase(Locale.ROOT));
 }

 private static LocalDate parseDate(String s) {
  if (s == null || s.isBlank()) return null;
  try { return LocalDate.parse(s.trim()); }
  catch (DateTimeParseException e) { throw new IllegalArgumentException("Dates must be yyyy-MM-dd, got '" + s + "'."); }
 }

 private static SlotView view(SlotDto s) {
  return new SlotView(s.id(), s.providerName(), s.serviceName(), s.startsAt().format(WHEN), s.endsAt().format(WHEN));
 }

 private static AppointmentView view(AppointmentDto a) {
  return new AppointmentView(a.id(), a.providerName(), a.serviceName(), a.startsAt().format(WHEN), a.status());
 }
}
