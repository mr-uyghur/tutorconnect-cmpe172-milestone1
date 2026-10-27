package edu.sjsu.tutorconnect.notification;
import edu.sjsu.tutorconnect.dto.NotificationRequest;

/** Boundary to the external notification system: one coarse-grained call per message. Implementations never throw. */
public interface NotificationGateway {
 void send(NotificationRequest request);
}
