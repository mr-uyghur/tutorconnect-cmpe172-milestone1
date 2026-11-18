package edu.sjsu.tutorconnect.exception;
/** The language model (an external service) failed or timed out. Mapped to 503; core booking is unaffected. */
public class AiUnavailableException extends RuntimeException {
 public AiUnavailableException(String message, Throwable cause) { super(message, cause); }
}
