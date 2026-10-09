package edu.sjsu.tutorconnect.repository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
 public record UserRow(long id, String fullName, String email, String passwordHash, String role) {}
 private final JdbcTemplate jdbc;
 public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

 public Optional<UserRow> findByEmail(String email) {
  return jdbc.query("SELECT user_id,full_name,email,password_hash,role FROM users WHERE LOWER(email)=LOWER(?)",
   (rs,n) -> new UserRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)), email).stream().findFirst();
 }

 public Optional<Long> findProviderId(long userId) {
  return jdbc.query("SELECT provider_id FROM providers WHERE user_id=?", (rs,n) -> rs.getLong(1), userId).stream().findFirst();
 }
}
