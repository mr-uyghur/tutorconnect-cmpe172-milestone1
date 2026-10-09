package edu.sjsu.tutorconnect.security;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security principal that also carries the database user id. */
public class AppUserDetails extends User {
 private final long id;
 private final String fullName;
 public AppUserDetails(long id, String fullName, String email, String passwordHash, String role) {
  super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  this.id = id; this.fullName = fullName;
 }
 public long getId() { return id; }
 public String getFullName() { return fullName; }
}
