package edu.sjsu.tutorconnect.repository;
import edu.sjsu.tutorconnect.dto.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogRepository {
 private final JdbcTemplate jdbc;
 public CatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
 // Video cue: "The home page gets its tutors and subjects from these two
 // database queries. The results become small response objects."
 public List<ProviderDto> findProviders() {
  return jdbc.query("SELECT p.provider_id, u.full_name, p.bio FROM providers p JOIN users u ON u.user_id=p.user_id ORDER BY p.provider_id",
   (rs,n) -> new ProviderDto(rs.getLong("provider_id"),rs.getString("full_name"),rs.getString("bio")));
 }
 public List<ServiceDto> findServices() {
  return jdbc.query("SELECT service_id,name,description FROM services ORDER BY service_id",
   (rs,n) -> new ServiceDto(rs.getLong("service_id"),rs.getString("name"),rs.getString("description")));
 }
}
