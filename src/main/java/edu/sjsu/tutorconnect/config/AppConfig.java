package edu.sjsu.tutorconnect.config;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

@Configuration
public class AppConfig {
 /** Injected so time-dependent rules can be tested with a fixed clock. */
 @Bean public Clock clock() { return Clock.systemDefaultZone(); }

 /** SQLite has no SQLState values, so map its error codes to Spring's standard exceptions. */
 @Bean public JdbcTemplate jdbcTemplate(DataSource dataSource) {
  var jdbc = new JdbcTemplate(dataSource);
  var fallback = new SQLStateSQLExceptionTranslator();
  jdbc.setExceptionTranslator((task, sql, ex) -> {
   if (ex.getErrorCode() == 19) return new DataIntegrityViolationException(task + ": " + sql, ex);
   if (ex.getErrorCode() == 5 || ex.getErrorCode() == 6)
    return new ConcurrencyFailureException("SQLite is busy; retry the transaction", ex);
   var translated = fallback.translate(task, sql, ex);
   return translated != null ? translated : new UncategorizedSQLException(task, sql, ex);
  });
  return jdbc;
 }
}
