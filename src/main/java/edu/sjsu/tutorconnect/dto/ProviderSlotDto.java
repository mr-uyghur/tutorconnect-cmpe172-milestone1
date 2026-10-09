package edu.sjsu.tutorconnect.dto;
import java.time.LocalDateTime;
public record ProviderSlotDto(long id, String serviceName, LocalDateTime startsAt, LocalDateTime endsAt, boolean booked, boolean hasHistory) {}
