package edu.sjsu.tutorconnect.repository;
import edu.sjsu.tutorconnect.dto.SlotDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class SlotRepository {
 private final JdbcTemplate jdbc;
 public SlotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
 // Video cue: "This query reads future slots. The last condition leaves out
 // any slot that already has an active booking, including the sample slot 2."
 public List<SlotDto> findAvailable() {
  return jdbc.query("""
   SELECT s.slot_id,s.provider_id,u.full_name,s.service_id,v.name,s.starts_at,s.ends_at
   FROM availability_slots s
   JOIN providers p ON p.provider_id=s.provider_id
   JOIN users u ON u.user_id=p.user_id
   JOIN services v ON v.service_id=s.service_id
   WHERE s.starts_at > CURRENT_TIMESTAMP
   AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id=s.slot_id AND a.status='BOOKED')
   ORDER BY s.starts_at,s.slot_id
   """, (rs,n) -> new SlotDto(rs.getLong("slot_id"),rs.getLong("provider_id"),rs.getString("full_name"),
    rs.getLong("service_id"),rs.getString("name"),rs.getTimestamp("starts_at").toLocalDateTime(),rs.getTimestamp("ends_at").toLocalDateTime()));
 }
}
