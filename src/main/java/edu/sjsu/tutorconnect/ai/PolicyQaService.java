package edu.sjsu.tutorconnect.ai;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Grounded Q&A (RAG): retrieve the closest policy sections, then ask Gemini to answer ONLY from them. If the answer is
 * not in the retrieved text the model must say so instead of guessing.
 */
@Service
public class PolicyQaService {
 static final String SYSTEM = """
  You answer questions about TutorConnect, a tutoring appointment service.
  Use ONLY the policy excerpts below. Do not use outside knowledge and do not invent rules, prices, or times.
  If the excerpts do not contain the answer, reply exactly: "I don't know based on TutorConnect's policies. \
  Please contact support@tutorconnect.example.test."
  Answer in at most four sentences.

  Policy excerpts:
  {context}
  """;

 public record Answer(String answer, List<String> sources) {}

 private final ChatClient chat;
 private final PolicyKnowledgeBase knowledge;

 public PolicyQaService(ChatClient.Builder builder, PolicyKnowledgeBase knowledge) {
  this.chat = builder.build();
  this.knowledge = knowledge;
 }

 public Answer ask(String question) {
  List<Document> docs = knowledge.search(question);
  String answer = chat.prompt()
   .system(s -> s.text(SYSTEM).param("context", context(docs)))
   .user(question)
   .call().content();
  return new Answer(answer, sources(docs));
 }

 static String context(List<Document> docs) {
  if (docs.isEmpty()) return "(no matching policy text)";
  return docs.stream().map(d -> "---\n" + d.getText()).collect(Collectors.joining("\n"));
 }

 static List<String> sources(List<Document> docs) {
  return docs.stream().map(d -> String.valueOf(d.getMetadata().get("section"))).toList();
 }
}
