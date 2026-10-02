package io.oryxos.core.provider;

public record TokenUsage(long inputTokens, long outputTokens, long totalTokens) {
  public TokenUsage {
    if (inputTokens < 0) {
      throw new IllegalArgumentException("inputTokens must be >= 0");
    }
    if (outputTokens < 0) {
      throw new IllegalArgumentException("outputTokens must be >= 0");
    }
    if (totalTokens < 0) {
      throw new IllegalArgumentException("totalTokens must be >= 0");
    }
  }
}
