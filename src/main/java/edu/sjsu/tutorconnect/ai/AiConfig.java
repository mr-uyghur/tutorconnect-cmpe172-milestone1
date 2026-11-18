package edu.sjsu.tutorconnect.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Milestone 4 AI wiring. The Gemini ChatModel, EmbeddingModel, ChatClient.Builder and ChatMemory (last 20 messages per
 * conversation) come from Spring AI auto-configuration; this class adds the vector store and turns on @Scheduled.
 */
@Configuration
@EnableScheduling
public class AiConfig {
 /** In-memory vector store: the knowledge file is small, so it is re-embedded on each start instead of persisted. */
 @Bean
 public VectorStore vectorStore(EmbeddingModel embeddingModel) {
  return SimpleVectorStore.builder(embeddingModel).build();
 }
}
