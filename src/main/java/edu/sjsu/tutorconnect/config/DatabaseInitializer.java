package edu.sjsu.tutorconnect.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates missing tables on startup and loads demo rows only once per database file. */
@Configuration
public class DatabaseInitializer {
 @Bean
 public ApplicationRunner initializeDatabase(DataSource dataSource, JdbcTemplate jdbc,
     PlatformTransactionManager transactions) {
  return args -> {
   new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
   new TransactionTemplate(transactions).executeWithoutResult(status -> {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM demo_seed_state WHERE id=1", Integer.class) == 0) {
     new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(dataSource);
     jdbc.update("INSERT INTO demo_seed_state(id) VALUES(1)");
    }
   });
  };
 }
}
