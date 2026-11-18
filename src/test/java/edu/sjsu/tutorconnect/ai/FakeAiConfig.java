package edu.sjsu.tutorconnect.ai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deterministic stand-ins for Gemini so AI tests run offline, fast, and the same every time. */
@TestConfiguration
public class FakeAiConfig {

 /** Chat model whose reply is scripted per test; it records every prompt it receives. */
 public static class FakeChatModel implements ChatModel {
  public final List<Prompt> prompts = new CopyOnWriteArrayList<>();
  public volatile Function<Prompt, String> responder = p -> "ok";
  @Override public ChatResponse call(Prompt prompt) {
   prompts.add(prompt);
   return new ChatResponse(List.of(new Generation(new AssistantMessage(responder.apply(prompt)))));
  }
  /** Text of every message (system, user, ...) in the n-th prompt. */
  public String text(int n) {
   return prompts.get(n).getInstructions().stream().map(m -> m.getText()).collect(java.util.stream.Collectors.joining("\n"));
  }
  public String lastText() { return text(prompts.size() - 1); }
  public void reset() { prompts.clear(); responder = p -> "ok"; }
 }

 /** Bag-of-words embedding: texts sharing words get similar vectors, which is enough to test retrieval. */
 public static class FakeEmbeddingModel implements EmbeddingModel {
  static final int DIM = 256;
  @Override public EmbeddingResponse call(EmbeddingRequest request) {
   var out = new java.util.ArrayList<Embedding>();
   for (int i = 0; i < request.getInstructions().size(); i++) out.add(new Embedding(vector(request.getInstructions().get(i)), i));
   return new EmbeddingResponse(out);
  }
  @Override public float[] embed(Document document) { return vector(document.getText()); }
  @Override public int dimensions() { return DIM; }
  static float[] vector(String text) {
   var v = new float[DIM];
   for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
    if (w.length() < 4) continue; // skip short stop words
    String stem = w.length() > 6 ? w.substring(0, 6) : w; // cancel/cancellation/cancelled share a bucket
    v[Math.floorMod(java.util.Arrays.hashCode(stem.getBytes(StandardCharsets.UTF_8)), DIM)] += 1f;
   }
   return v;
  }
 }

 @Bean @Primary public FakeChatModel fakeChatModel() { return new FakeChatModel(); }
 @Bean @Primary public FakeEmbeddingModel fakeEmbeddingModel() { return new FakeEmbeddingModel(); }
}
