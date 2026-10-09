package edu.sjsu.tutorconnect.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
 @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

 @Bean
 public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  http
   .authorizeHttpRequests(a -> a
    .requestMatchers("/login", "/forbidden", "/error", "/css/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/", "/slots", "/api/**").permitAll()
    .requestMatchers("/provider/**").hasRole("PROVIDER")
    .requestMatchers("/slots/**", "/appointments/**").hasRole("CUSTOMER")
    .anyRequest().authenticated())
   .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
   .logout(l -> l.logoutSuccessUrl("/"))
   // A signed-in user with the wrong role gets a 403 page, not a redirect to the login form.
   .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
    res.setStatus(403);
    req.getRequestDispatcher("/forbidden").forward(req, res);
   }))
   .csrf(Customizer.withDefaults());
  return http.build();
 }
}
