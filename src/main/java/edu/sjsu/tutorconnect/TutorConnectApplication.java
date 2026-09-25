package edu.sjsu.tutorconnect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class TutorConnectApplication {
 // Video cue: "This is where the app starts. Spring Boot starts the web server
 // and finds the controller, service, and repository classes in this package."
 public static void main(String[] args) { SpringApplication.run(TutorConnectApplication.class, args); }
}
