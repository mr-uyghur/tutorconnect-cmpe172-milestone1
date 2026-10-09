package edu.sjsu.tutorconnect.repository;
import edu.sjsu.tutorconnect.dto.AppointmentDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
 private final JdbcTemplate jdbc;
 public AppointmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

 // COMPLETED is derived: a BOOKED appointment whose slot has already ended.
 private static final String SELECT = """
  SELECT a.appointment_id,a.slot_id,a.customer_id,c.full_name AS customer_name,pu.full_name AS provider_name,v.name AS service_name,
   s.starts_at,s.ends_at,
   CASE WHEN a.status='BOOKED' AND s.ends_at <= datetime('now','localtime') THEN 'COMPLETED' ELSE a.status END AS status
  FROM appointments a
  JOIN users c ON c.user_id=a.customer_id
  JOIN availability_slots s ON s.slot_id=a.slot_id
  JOIN providers p ON p.provider_id=s.provider_id
  JOIN users pu ON pu.user_id=p.user_id
  JOIN services v ON v.service_id=s.service_id
  """;

 private static final RowMapper<AppointmentDto> MAPPER = (rs,n) -> new AppointmentDto(rs.getLong("appointment_id"),rs.getLong("slot_id"),
  rs.getLong("customer_id"),rs.getString("customer_name"),rs.getString("provider_name"),rs.getString("service_name"),
  rs.getTimestamp("starts_at").toLocalDateTime(),rs.getTimestamp("ends_at").toLocalDateTime(),rs.getString("status"));

 public boolean existsBooked(long slotId) {
  return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM appointments WHERE slot_id=? AND status='BOOKED')", Boolean.class, slotId);
 }

 /** Inserts a BOOKED row. The partial unique index is the final guard against double-booking. */
 public long insertBooked(long customerId, long slotId) {
  var keys = new GeneratedKeyHolder();
  jdbc.update(con -> {
   var ps = con.prepareStatement("INSERT INTO appointments(customer_id,slot_id,status) VALUES (?,?,'BOOKED')", new String[]{"appointment_id"});
   ps.setLong(1, customerId); ps.setLong(2, slotId);
   return ps;
  }, keys);
  return keys.getKey().longValue();
 }

 public Optional<AppointmentDto> findById(long id) {
  return jdbc.query(SELECT + " WHERE a.appointment_id=?", MAPPER, id).stream().findFirst();
 }

 public List<AppointmentDto> findByCustomer(long customerId) {
  return jdbc.query(SELECT + " WHERE a.customer_id=? ORDER BY s.starts_at DESC, a.appointment_id DESC", MAPPER, customerId);
 }

 public List<AppointmentDto> findByProviderUser(long providerUserId) {
  return jdbc.query(SELECT + " WHERE pu.user_id=? AND a.status<>'CANCELLED' ORDER BY s.starts_at, a.appointment_id", MAPPER, providerUserId);
 }

 /** Owner-only, still-upcoming cancel in a single statement. Returns rows updated (0 or 1). */
 public int cancelOwnUpcoming(long appointmentId, long customerId) {
  return jdbc.update("""
   UPDATE appointments SET status='CANCELLED'
   WHERE appointment_id=? AND customer_id=? AND status='BOOKED'
   AND EXISTS (SELECT 1 FROM availability_slots s WHERE s.slot_id=appointments.slot_id AND s.starts_at > datetime('now','localtime'))
   """, appointmentId, customerId);
 }
}
