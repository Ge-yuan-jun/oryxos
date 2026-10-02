package io.oryxos.core.message;

import java.util.Objects;

public record ToolCall(String id, String name, String arguments) {
  public ToolCall {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(arguments, "arguments");
  }
}
