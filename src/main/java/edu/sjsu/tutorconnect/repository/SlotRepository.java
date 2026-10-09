package edu.sjsu.tutorconnect.repository;
import edu.sjsu.tutorconnect.dto.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {
 private final JdbcTemplate jdbc;
 public SlotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

 // Future slots with no active booking. Shared by the listing and the count so pagination stays consistent.
 private static final String OPEN_SLOTS = """
  FROM availability_slots s
  JOIN providers p ON p.provider_id=s.provider_id
  JOIN users u ON u.user_id=p.user_id
  JOIN services v ON v.service_id=s.service_id
  WHERE s.starts_at > CURRENT_TIMESTAMP
  AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id=s.slot_id AND a.status='BOOKED')
  """;

 private static final RowMapper<SlotDto> SLOT_MAPPER = (rs,n) -> new SlotDto(rs.getLong("slot_id"),rs.getLong("provider_id"),
  rs.getString("full_name"),rs.getLong("service_id"),rs.getString("name"),
  rs.getTimestamp("starts_at").toLocalDateTime(),rs.getTimestamp("ends_at").toLocalDateTime());

 /** One page of open slots. Filter values are always bound as parameters, never concatenated. */
 public Page<SlotDto> findAvailable(SlotFilter f, int page, int size) {
  var where = new StringBuilder();
  var args = new ArrayList<Object>();
  if (f.providerId() != null) { where.append(" AND s.provider_id=?"); args.add(f.providerId()); }
  if (f.serviceId() != null) { where.append(" AND s.service_id=?"); args.add(f.serviceId()); }
  if (f.date() != null) { where.append(" AND CAST(s.starts_at AS DATE)=?"); args.add(java.sql.Date.valueOf(f.date())); }
  long total = jdbc.queryForObject("SELECT COUNT(*) " + OPEN_SLOTS + where, Long.class, args.toArray());
  var pageArgs = new ArrayList<>(args);
  pageArgs.add(size);
  pageArgs.add((long) page * size);
  List<SlotDto> items = jdbc.query("SELECT s.slot_id,s.provider_id,u.full_name,s.service_id,v.name,s.starts_at,s.ends_at "
   + OPEN_SLOTS + where + " ORDER BY s.starts_at,s.slot_id LIMIT ? OFFSET ?", SLOT_MAPPER, pageArgs.toArray());
  return new Page<>(items, page, size, total);
 }

 public Optional<SlotRow> findRow(long slotId) {
  return jdbc.query("SELECT slot_id,provider_id,service_id,starts_at,ends_at,version FROM availability_slots WHERE slot_id=?",
   (rs,n) -> new SlotRow(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getTimestamp(4).toLocalDateTime(),rs.getTimestamp(5).toLocalDateTime(),rs.getInt(6)), slotId).stream().findFirst();
 }

 public Optional<SlotDto> findOpenDetail(long slotId) {
  return jdbc.query("SELECT s.slot_id,s.provider_id,u.full_name,s.service_id,v.name,s.starts_at,s.ends_at "
   + OPEN_SLOTS + " AND s.slot_id=?", SLOT_MAPPER, slotId).stream().findFirst();
 }

 /** Optimistic check: succeeds only if nobody changed the row since it was read. Returns rows updated (0 or 1). */
 public int bumpVersion(long slotId, int expectedVersion) {
  return jdbc.update("UPDATE availability_slots SET version=version+1 WHERE slot_id=? AND version=?", slotId, expectedVersion);
 }

 public long insert(long providerId, long serviceId, LocalDateTime startsAt) {
  var keys = new GeneratedKeyHolder();
  jdbc.update(con -> {
   var ps = con.prepareStatement("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES (?,?,?,?)", new String[]{"slot_id"});
   ps.setLong(1, providerId); ps.setLong(2, serviceId);
   ps.setTimestamp(3, Timestamp.valueOf(startsAt)); ps.setTimestamp(4, Timestamp.valueOf(startsAt.plusHours(1)));
   return ps;
  }, keys);
  return keys.getKey().longValue();
 }

 /** Deletes only the provider's own slot, and only if it has no appointment history (the FK would block it anyway). */
 public int deleteIfUnused(long slotId, long providerId) {
  return jdbc.update("DELETE FROM availability_slots WHERE slot_id=? AND provider_id=? "
   + "AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id=?)", slotId, providerId, slotId);
 }

 public List<ProviderSlotDto> findByProvider(long providerId) {
  return jdbc.query("""
   SELECT s.slot_id,v.name,s.starts_at,s.ends_at,
    EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id=s.slot_id AND a.status='BOOKED') AS booked,
    EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id=s.slot_id) AS has_history
   FROM availability_slots s JOIN services v ON v.service_id=s.service_id
   WHERE s.provider_id=? ORDER BY s.starts_at
   """, (rs,n) -> new ProviderSlotDto(rs.getLong("slot_id"),rs.getString("name"),rs.getTimestamp("starts_at").toLocalDateTime(),
    rs.getTimestamp("ends_at").toLocalDateTime(),rs.getBoolean("booked"),rs.getBoolean("has_history")), providerId);
 }
}
