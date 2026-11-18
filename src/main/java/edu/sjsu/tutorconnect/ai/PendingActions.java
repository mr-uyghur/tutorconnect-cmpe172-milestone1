package edu.sjsu.tutorconnect.ai;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Server-side confirm-before-write. A write tool first records a proposal for the conversation; the matching confirm
 * tool only runs it if the proposal was made in an EARLIER user turn. So the model cannot book or cancel in the same
 * turn it proposed, no matter what the prompt says: the user has to send another message (their confirmation) first.
 */
@Component
public class PendingActions {
 public enum Kind { BOOK, CANCEL }
 /** subject is remembered so a booking conflict can offer alternatives in the same subject. */
 public record Pending(Kind kind, long targetId, String subject, long turn) {}

 private final Map<String, Long> turns = new ConcurrentHashMap<>();
 private final Map<String, Pending> pending = new ConcurrentHashMap<>();

 /** Called once per user message; returns that message's turn number within the conversation. */
 public long beginTurn(String conversationKey) {
  return turns.merge(conversationKey, 1L, Long::sum);
 }

 /**
  * Replaces any earlier proposal in this conversation. Re-proposing the SAME action keeps its original turn: the model
  * often re-checks the slot before confirming, and the student already saw that exact summary, so resetting the turn
  * would make the confirmation fail forever.
  */
 public void propose(String conversationKey, Kind kind, long targetId, String subject, long turn) {
  pending.merge(conversationKey, new Pending(kind, targetId, subject, turn),
   (old, now) -> old.kind() == now.kind() && old.targetId() == now.targetId() ? old : now);
 }

 /** Removes and returns the proposal only if it is of this kind and was made before the current turn. */
 public Optional<Pending> takeConfirmable(String conversationKey, Kind kind, long currentTurn) {
  var result = new Pending[1];
  pending.computeIfPresent(conversationKey, (k, p) -> {
   if (p.kind() == kind && p.turn() < currentTurn) { result[0] = p; return null; }
   return p;
  });
  return Optional.ofNullable(result[0]);
 }

 public Optional<Pending> peek(String conversationKey) {
  return Optional.ofNullable(pending.get(conversationKey));
 }
}
