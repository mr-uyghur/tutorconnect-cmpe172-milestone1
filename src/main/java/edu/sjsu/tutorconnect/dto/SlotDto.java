package edu.sjsu.tutorconnect.dto;
import java.time.LocalDateTime;
public record SlotDto(long id, long providerId, String providerName, long serviceId, String serviceName, LocalDateTime startsAt, LocalDateTime endsAt) {}
