# Contract: ProviderService

**Feature**: 001-provider-abstraction | **Date**: 2026-10-02

## Overview

`ProviderService` 是 OryxOS 对接 LLM 的唯一门面。上层（ReAct 循环、未来的 AgentService）通过此接口完成所有 LLM 调用，不直接接触 Spring AI 的 `ChatModel`。

## Interface Definition

```java
package com.oryxos.core.provider;

import java.util.List;
import com.oryxos.core.message.Message;
import com.oryxos.core.tool.OryxTool;

public interface ProviderService {

    /**
     * 调用 LLM。
     *
     * @param providerName  provider 唯一标识（对应 application.yaml 配置）
     * @param model         可选的模型覆盖，null 时使用 provider 默认模型
     * @param messages      对话消息列表（含 system/user/assistant/tool）
     * @param tools         当前可用的工具列表（可为空）
     * @return LLM 响应（含文本内容、tool calls、token usage）
     * @throws ProviderNotFoundException  providerName 不在映射表中
     * @throws ProviderCallException      LLM 调用失败（网络/5xx/解析异常）
     */
    LlmResponse call(String providerName,
                      String model,
                      List<Message> messages,
                      List<OryxTool> tools);

    /**
     * 查询已注册的 provider name 列表。
     */
    List<String> listProviders();

    /**
     * 检查某个 provider name 是否已注册。
     */
    boolean hasProvider(String providerName);
}
```

## Request / Response Contract

### Input

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| providerName | String | YES | 已注册的 provider name |
| model | String | NO | 模型覆盖（null = 使用配置默认值） |
| messages | List\<Message\> | YES | 至少包含一条 USER 消息 |
| tools | List\<OryxTool\> | NO | 可用工具列表（空列表 = 不启用 Function Calling） |

### Output: `LlmResponse`

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| content | String | YES | LLM 文本响应（有 tool_call 时可能为空） |
| toolCalls | List\<ToolCall\> | YES | 工具调用请求列表 |
| tokenUsage | TokenUsage | NO | token 用量 |
| finishReason | String | YES | 结束原因 |

### Error Cases

| Exception | Condition | HTTP Analogy |
|-----------|-----------|--------------|
| ProviderNotFoundException | providerName 不在映射表中 | 404 |
| ProviderCallException | 网络超时、HTTP 5xx、响应解析失败 | 502 |
| IllegalArgumentException | messages 为空或无 USER 消息 | 400 |

## Side Effects

每次调用（无论成功或失败）**必须**写入 `llm_calls` 审计表：
- 成功：`success=true`，记录 token usage 和 duration
- 失败：`success=false`，记录 error_message 和 duration

## Thread Safety

`ProviderService` 实现必须是线程安全的：
- 内部 `Map<String, ChatModel>` 在启动时一次性构建，运行时只读
- `LlmCallAuditor` 的 JPA `save()` 由 Spring Data 保证线程安全
- 虚拟线程下每个请求独立，无共享可变状态

## Configuration Contract

```yaml
oryxos:
  providers:
    - name: deepseek
      type: openai
      model: deepseek-chat
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    - name: kimi
      type: openai
      model: moonshot-v1-8k
      api-key: ${KIMI_API_KEY}
      base-url: https://api.moonshot.cn/v1
    - name: mock
      type: mock
      model: mock-model
```

### Configuration Validation (startup)

| Rule | Behavior on Violation |
|------|----------------------|
| provider name 不能重复 | 启动失败 + 错误信息指出重复 name |
| 非 mock 的 api-key 不能为空/空白 | 启动失败 + 错误信息指出缺失的 key 和环境变量名 |
| type 必须是已知类型 | 启动失败 + 错误信息列出支持的类型 |
| providers 列表不能为空 | 启动失败 + 提示至少配一个 provider |
