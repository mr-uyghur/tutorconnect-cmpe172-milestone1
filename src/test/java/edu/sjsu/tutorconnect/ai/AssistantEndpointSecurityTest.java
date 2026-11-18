package edu.sjsu.tutorconnect.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** RBAC and CSRF on the AI endpoints: the agent acts as the signed-in student, so only CUSTOMER accounts may use it. */
@SpringBootTest @AutoConfigureMockMvc
class AssistantEndpointSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean BookingAssistant assistant;
 @MockitoBean ReminderAgent reminders;
 static final String BODY = "{\"conversationId\":\"c-1\",\"message\":\"hello\"}";

 @Test void anonymousIsSentToLogin() throws Exception {
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
   .andExpect(status().is3xxRedirection());
  verifyNoInteractions(assistant);
 }

 @Test @WithUserDetails("alex@example.test") void providerIsForbidden() throws Exception {
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
   .andExpect(status().isForbidden());
  verifyNoInteractions(assistant);
 }

 @Test @WithUserDetails("maya@example.test") void missingCsrfTokenIsForbidden() throws Exception {
  mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
  verifyNoInteractions(assistant);
 }

 @Test @WithUserDetails("maya@example.test") void customerChatsAsThemself() throws Exception {
  when(assistant.chat(eq(1L), eq("Maya Student"), eq("c-1"), eq("hello"))).thenReturn("Hi Maya!");
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
   .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value("c-1")).andExpect(jsonPath("$.reply").value("Hi Maya!"));
 }

 @Test @WithUserDetails("maya@example.test") void modelFailureIs503NotA500() throws Exception {
  when(assistant.chat(anyLong(), any(), any(), any())).thenThrow(new RuntimeException("Gemini timeout"));
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
   .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
 }

 @Test @WithUserDetails("maya@example.test") void invalidInputIsRejected() throws Exception {
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/agent/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"conversationId\":\"../x\",\"message\":\"hi\"}"))
   .andExpect(status().isBadRequest());
 }

 @Test @WithUserDetails("alex@example.test") void providerCanTriggerReminders() throws Exception {
  when(reminders.run()).thenReturn(new ReminderAgent.RunResult(0, 0, 0, java.util.List.of()));
  mvc.perform(post("/provider/agents/reminders/run").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.candidates").exists());
 }

 @Test @WithUserDetails("maya@example.test") void customerCannotTriggerReminders() throws Exception {
  mvc.perform(post("/provider/agents/reminders/run").with(csrf())).andExpect(status().isForbidden());
  verifyNoInteractions(reminders);
 }
}
