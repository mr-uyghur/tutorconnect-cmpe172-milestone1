package edu.sjsu.tutorconnect.notification;
/** Published inside the transaction that changed the appointment; delivered only if that transaction commits. */
public record AppointmentEvent(long appointmentId, Kind kind) {
 public enum Kind { BOOKED, CANCELLED }
}
