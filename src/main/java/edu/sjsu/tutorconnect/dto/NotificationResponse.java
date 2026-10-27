package edu.sjsu.tutorconnect.dto;
/** duplicate is true when the idempotency key was already seen and nothing new was stored. */
public record NotificationResponse(boolean accepted, boolean duplicate) {}
