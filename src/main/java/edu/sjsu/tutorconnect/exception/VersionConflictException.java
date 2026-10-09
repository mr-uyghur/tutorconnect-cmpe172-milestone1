package edu.sjsu.tutorconnect.exception;
/** The optimistic version check lost a race. Internal and retryable; never shown to clients. */
public class VersionConflictException extends RuntimeException { public VersionConflictException() { super("slot version changed"); } }
