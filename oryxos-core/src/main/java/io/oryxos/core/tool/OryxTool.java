package io.oryxos.core.tool;

public interface OryxTool {
  String getName();

  String getDescription();

  Class<?> getParameterSchema();

  String execute(String argsJson);
}
