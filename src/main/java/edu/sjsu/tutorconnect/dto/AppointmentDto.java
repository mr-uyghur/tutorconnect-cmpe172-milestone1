package edu.sjsu.tutorconnect.dto;
import java.time.LocalDateTime;
/** status is BOOKED, CANCELLED, or COMPLETED (derived: a BOOKED row whose slot has ended). */
public record AppointmentDto(long id, long slotId, long customerId, String customerName, String providerName,
  String serviceName, LocalDateTime startsAt, LocalDateTime endsAt, String status) {}
