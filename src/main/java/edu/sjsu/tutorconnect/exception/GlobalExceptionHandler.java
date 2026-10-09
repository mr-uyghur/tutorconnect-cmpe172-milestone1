package edu.sjsu.tutorconnect.exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to a standard status code; clients never see a stack trace. */
@ControllerAdvice
public class GlobalExceptionHandler {
 private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

 @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
 public Object notFound(Exception e, HttpServletRequest r) { return respond(r, HttpStatus.NOT_FOUND, e instanceof NotFoundException ? e.getMessage() : "Page not found."); }

 @ExceptionHandler(SlotConflictException.class)
 public Object conflict(SlotConflictException e, HttpServletRequest r) { return respond(r, HttpStatus.CONFLICT, e.getMessage()); }

 @ExceptionHandler(VersionConflictException.class)
 public Object versionConflict(HttpServletRequest r) { return respond(r, HttpStatus.CONFLICT, "This slot was just booked. Please choose another slot."); }

 @ExceptionHandler(AccessDeniedException.class)
 public Object forbidden(HttpServletRequest r) { return respond(r, HttpStatus.FORBIDDEN, "You do not have permission to do that."); }

 @ExceptionHandler(BadRequestException.class)
 public Object badRequest(BadRequestException e, HttpServletRequest r) { return respond(r, HttpStatus.BAD_REQUEST, e.getMessage()); }

 @ExceptionHandler(BindException.class)
 public Object invalidForm(BindException e, HttpServletRequest r) {
  var fe = e.getFieldError();
  return respond(r, HttpStatus.BAD_REQUEST, fe == null ? "Invalid request." : "Invalid value for '" + fe.getField() + "'.");
 }

 @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
 public Object invalidParam(HttpServletRequest r) { return respond(r, HttpStatus.BAD_REQUEST, "One or more request parameters are invalid."); }

 @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
 public Object method(HttpServletRequest r) { return respond(r, HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed."); }

 @ExceptionHandler(DataIntegrityViolationException.class)
 public Object integrity(DataIntegrityViolationException e, HttpServletRequest r) {
  log.warn("Constraint violation: {}", e.getMostSpecificCause().getMessage());
  return respond(r, HttpStatus.CONFLICT, "That change conflicts with existing data.");
 }

 @ExceptionHandler(Exception.class)
 public Object unexpected(Exception e, HttpServletRequest r) {
  log.error("Unhandled error on {} {}", r.getMethod(), r.getRequestURI(), e);
  return respond(r, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
 }

 /** JSON for /api paths, an HTML error page otherwise. */
 private Object respond(HttpServletRequest r, HttpStatus status, String message) {
  if (r.getRequestURI().startsWith("/api/"))
   return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase(), "message", message));
  var mv = new ModelAndView("error", Map.of("status", status.value(), "reason", status.getReasonPhrase(), "message", message));
  mv.setStatus(status);
  return mv;
 }
}
