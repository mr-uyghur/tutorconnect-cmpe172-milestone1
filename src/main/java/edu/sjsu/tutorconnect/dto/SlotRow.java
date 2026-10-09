package edu.sjsu.tutorconnect.dto;
import java.time.LocalDateTime;
/** Raw slot row including the optimistic-lock version; used by the booking transaction. */
public record SlotRow(long id, long providerId, long serviceId, LocalDateTime startsAt, LocalDateTime endsAt, int version) {}
