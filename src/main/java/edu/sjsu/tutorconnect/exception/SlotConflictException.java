package edu.sjsu.tutorconnect.exception;
/** The slot (or schedule position) is already taken; maps to HTTP 409. */
public class SlotConflictException extends RuntimeException { public SlotConflictException(String m) { super(m); } }
