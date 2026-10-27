package edu.sjsu.tutorconnect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class OpsEndpointsTest {
 @Autowired MockMvc mvc;
 @Test void healthIsPublicAndReportsDatabase() throws Exception {
  mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
   .andExpect(jsonPath("$.components.db.status").value("UP"));
 }
 @Test void metricsRequireLogin() throws Exception {
  mvc.perform(get("/metrics")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
 }
 @Test @WithMockUser(roles = "CUSTOMER") void metricsForbiddenForCustomers() throws Exception {
  mvc.perform(get("/metrics")).andExpect(status().isForbidden());
 }
 @Test @WithMockUser(roles = "PROVIDER") void metricsAvailableToProviders() throws Exception {
  mvc.perform(get("/metrics")).andExpect(status().isOk()).andExpect(jsonPath("$.names").isArray());
  mvc.perform(get("/metrics/jvm.memory.used")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("jvm.memory.used"));
 }
}
