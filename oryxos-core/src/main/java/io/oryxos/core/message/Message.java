package io.oryxos.core.message;

import java.util.List;
import java.util.Objects;

public record Message(
    MessageRole role, String content, List<ToolCall> toolCalls, String toolCallId, String name) {

  public Message {
    Objects.requireNonNull(role, "role");
    toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
  }

  public static Message userMessage(String content) {
    return new Message(MessageRole.USER, content, null, null, null);
  }

  public static Message systemMessage(String content) {
    return new Message(MessageRole.SYSTEM, content, null, null, null);
  }

  public static Message assistantMessage(String content, List<ToolCall> toolCalls) {
    return new Message(MessageRole.ASSISTANT, content, toolCalls, null, null);
  }

  public static Message toolMessage(String toolCallId, String name, String content) {
    return new Message(MessageRole.TOOL, content, null, toolCallId, name);
  }
}
