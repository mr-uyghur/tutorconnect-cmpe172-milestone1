package edu.sjsu.tutorconnect.notification;
import edu.sjsu.tutorconnect.dto.NotificationRequest;

/**
 * Boundary to the external notification system: one coarse-grained call per message. Implementations never throw;
 * the result says whether the message was delivered (callers that track delivery, like the reminder agent, use it).
 */
public interface NotificationGateway {
 boolean send(NotificationRequest request);
}
