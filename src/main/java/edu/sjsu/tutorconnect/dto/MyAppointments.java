package edu.sjsu.tutorconnect.dto;
import java.util.List;
public record MyAppointments(List<AppointmentDto> upcoming, List<AppointmentDto> history) {}
