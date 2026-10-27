package edu.sjsu.tutorconnect.config;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class NotificationConfig {
 private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

 /** Small and bounded: if the queue fills, new notifications are dropped (and counted) instead of blocking requests. */
 @Bean(name = "notificationExecutor")
 public ThreadPoolTaskExecutor notificationExecutor(MeterRegistry metrics) {
  var ex = new ThreadPoolTaskExecutor();
  ex.setCorePoolSize(2); ex.setMaxPoolSize(4); ex.setQueueCapacity(100);
  ex.setThreadNamePrefix("notify-");
  // On shutdown, finish notifications already queued (bounded wait) instead of dropping them.
  ex.setWaitForTasksToCompleteOnShutdown(true); ex.setAwaitTerminationSeconds(10);
  ex.setRejectedExecutionHandler((r, pool) -> {
   metrics.counter("tutorconnect.notifications", "result", "dropped").increment();
   log.error("Notification queue full; notification dropped");
  });
  return ex;
 }
}
