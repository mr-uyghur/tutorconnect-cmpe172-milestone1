package edu.sjsu.tutorconnect.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** RAG: the knowledge file is split, embedded and retrieved, and the answer prompt contains only retrieved policy text. */
@SpringBootTest @AutoConfigureMockMvc @Import(FakeAiConfig.class)
class PolicyRagTest {
 @Autowired PolicyKnowledgeBase knowledge;
 @Autowired PolicyQaService qa;
 @Autowired FakeAiConfig.FakeChatModel llm;

 @BeforeEach void setUp() { llm.reset(); }

 @Test void knowledgeFileIsSplitIntoOneDocumentPerSection() {
  var docs = PolicyKnowledgeBase.splitSections(knowledge.read());
  assertEquals(12, docs.size());
  assertTrue(docs.stream().anyMatch(d -> "Cancellation policy".equals(d.getMetadata().get("section"))));
 }

 @Test void retrievalFindsTheRelevantSection() {
  var top = knowledge.search("How do I cancel a booked session?").get(0);
  assertEquals("Cancellation policy", top.getMetadata().get("section"));
  assertEquals("Hours of operation", knowledge.search("What hours are tutoring sessions open on Saturday?").get(0).getMetadata().get("section"));
 }

 @Test void answerIsGroundedInRetrievedExcerptsAndCitesThem() {
  llm.responder = p -> "You can cancel for free any time before the session starts.";
  var answer = qa.ask("Can I cancel my session?");
  assertEquals("You can cancel for free any time before the session starts.", answer.answer());
  assertTrue(answer.sources().contains("Cancellation policy"), answer.sources().toString());
  String prompt = llm.text(0);
  assertTrue(prompt.contains("Use ONLY the policy excerpts"), prompt);
  assertTrue(prompt.contains("12 hours notice"), "retrieved cancellation text is in the prompt");
 }
}
