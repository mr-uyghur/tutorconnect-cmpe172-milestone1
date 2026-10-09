package edu.sjsu.tutorconnect.dto;
import java.time.LocalDate;
/** Optional filters for the slot search; null means "any". */
public record SlotFilter(Long providerId, Long serviceId, LocalDate date) {}
