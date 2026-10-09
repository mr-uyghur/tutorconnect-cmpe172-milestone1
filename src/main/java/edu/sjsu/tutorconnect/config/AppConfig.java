package edu.sjsu.tutorconnect.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
 /** Injected so time-dependent rules can be tested with a fixed clock. */
 @Bean public Clock clock() { return Clock.systemDefaultZone(); }
}
