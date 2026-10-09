package edu.sjsu.tutorconnect.security;
import edu.sjsu.tutorconnect.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads the account and its role from the users table over JDBC. */
@Service
public class JdbcUserDetailsService implements UserDetailsService {
 private final UserRepository users;
 public JdbcUserDetailsService(UserRepository users) { this.users = users; }

 @Override
 public UserDetails loadUserByUsername(String email) {
  var u = users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  // Accounts without a password hash cannot sign in.
  if (u.passwordHash() == null) throw new UsernameNotFoundException("Unknown user");
  return new AppUserDetails(u.id(), u.fullName(), u.email(), u.passwordHash(), u.role());
 }
}
