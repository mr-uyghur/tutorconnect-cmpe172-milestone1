package edu.sjsu.tutorconnect.controller;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;

/** Target of the access-denied handler: renders the 403 page (or JSON for /api paths) with status 403. */
@Controller
public class ForbiddenController {
 @RequestMapping("/forbidden")
 public Object forbidden(HttpServletRequest r) {
  String original = String.valueOf(r.getAttribute("jakarta.servlet.forward.request_uri"));
  String msg = "You do not have permission to view that page.";
  if (original.startsWith("/api/"))
   return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403, "error", "Forbidden", "message", msg));
  var mv = new ModelAndView("error", Map.of("status", 403, "reason", "Forbidden", "message", msg));
  mv.setStatus(HttpStatus.FORBIDDEN);
  return mv;
 }
}
