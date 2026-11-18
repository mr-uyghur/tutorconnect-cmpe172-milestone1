package edu.sjsu.tutorconnect.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Retrieval side of RAG. The policy/FAQ file is split into one document per "##" section, embedded with the Gemini
 * embedding model, and stored in the vector store. Questions are embedded the same way and the closest sections are
 * returned as grounding context.
 */
@Component
public class PolicyKnowledgeBase {
 private static final Logger log = LoggerFactory.getLogger(PolicyKnowledgeBase.class);
 private final VectorStore vectorStore;
 private final Resource source;
 private final boolean ingestOnStartup;
 private final int topK;
 private final AtomicBoolean loaded = new AtomicBoolean();

 public PolicyKnowledgeBase(VectorStore vectorStore,
   @Value("classpath:knowledge/tutorconnect-policies.md") Resource source,
   @Value("${tutorconnect.rag.ingest-on-startup:true}") boolean ingestOnStartup,
   @Value("${tutorconnect.rag.top-k:4}") int topK) {
  this.vectorStore = vectorStore; this.source = source; this.ingestOnStartup = ingestOnStartup; this.topK = topK;
 }

 /** Startup ingestion. A failure (no key, network) is logged and retried lazily on the first question. */
 @EventListener(ApplicationReadyEvent.class)
 public void ingestOnStartup() {
  if (!ingestOnStartup) return;
  try { ensureLoaded(); }
  catch (RuntimeException e) { log.warn("Knowledge base ingestion failed at startup; will retry on first use: {}", e.toString()); }
 }

 /** Embeds and stores the knowledge file once. Synchronized so concurrent first questions do not ingest twice. */
 public synchronized void ensureLoaded() {
  if (loaded.get()) return;
  var docs = splitSections(read());
  vectorStore.add(docs);
  loaded.set(true);
  log.info("Knowledge base ingested: {} sections from {}", docs.size(), source.getFilename());
 }

 /** The sections most similar to the question, best first. */
 public List<Document> search(String question) {
  ensureLoaded();
  return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(topK).build());
 }

 String read() {
  try { return source.getContentAsString(StandardCharsets.UTF_8); }
  catch (IOException e) { throw new IllegalStateException("Cannot read knowledge file " + source, e); }
 }

 /** One document per "## Heading" section; the heading is kept in the text and in metadata (shown as the source). */
 static List<Document> splitSections(String markdown) {
  var docs = new ArrayList<Document>();
  String title = null;
  var body = new StringBuilder();
  for (String line : markdown.split("\\R")) {
   if (line.startsWith("## ")) {
    addSection(docs, title, body);
    title = line.substring(3).trim();
    body.setLength(0);
   } else if (title != null) {
    body.append(line).append('\n');
   }
  }
  addSection(docs, title, body);
  return docs;
 }

 private static void addSection(List<Document> docs, String title, StringBuilder body) {
  if (title == null || body.toString().isBlank()) return;
  String id = java.util.UUID.nameUUIDFromBytes(("policy:" + title).getBytes(StandardCharsets.UTF_8)).toString();
  docs.add(new Document(id, title + "\n" + body.toString().trim(), Map.of("section", title)));
 }
}
