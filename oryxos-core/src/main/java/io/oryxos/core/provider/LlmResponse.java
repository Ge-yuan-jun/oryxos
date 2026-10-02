package io.oryxos.core.provider;

import io.oryxos.core.message.ToolCall;
import java.util.List;
import java.util.Objects;

public record LlmResponse(
    String content, List<ToolCall> toolCalls, TokenUsage tokenUsage, String finishReason) {

  public LlmResponse {
    Objects.requireNonNull(tokenUsage, "tokenUsage");
    toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
  }

  public boolean hasToolCalls() {
    return toolCalls != null && !toolCalls.isEmpty();
  }
}
