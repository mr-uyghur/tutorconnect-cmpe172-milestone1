package edu.sjsu.tutorconnect.dto;
import jakarta.validation.constraints.*;
/** The whole message in one call. idempotencyKey lets the receiver ignore a retried or duplicated delivery. */
public record NotificationRequest(@NotBlank @Size(max = 100) String idempotencyKey, @NotBlank @Size(max = 254) String recipient,
  @NotNull NotificationType type, @NotBlank @Size(max = 500) String message, Long appointmentId) {}
