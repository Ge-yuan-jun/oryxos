package io.oryxos.provider;

import io.oryxos.core.tool.OryxTool;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 把 {@link OryxTool} 三要素（name/description/参数 Class）翻译成 Spring AI 的工具描述。
 *
 * <p>只翻译、不执行：产物的 {@code call()} 永远抛异常——Provider 层关闭框架内部工具执行后的 第二道保险，实际执行由上层 ReAct 循环控制。
 */
final class FunctionCallingAdapter {

  private FunctionCallingAdapter() {}

  static List<ToolCallback> toToolCallbacks(List<OryxTool> tools) {
    return tools.stream().map(SchemaOnlyCallback::new).map(ToolCallback.class::cast).toList();
  }

  /** 纯 schema 描述载体：不携带任何可执行逻辑。 */
  static final class SchemaOnlyCallback implements ToolCallback {

    private final ToolDefinition definition;

    SchemaOnlyCallback(OryxTool tool) {
      this.definition =
          DefaultToolDefinition.builder()
              .name(tool.getName())
              .description(tool.getDescription())
              .inputSchema(JsonSchemaGenerator.generate(tool.getParameterSchema()))
              .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public String call(String toolInput) {
      throw new IllegalStateException("Provider 层只翻译工具、不执行工具: " + definition.name());
    }
  }
}
