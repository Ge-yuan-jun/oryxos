# Quickstart Validation Guide: Provider 抽象

**Feature**: 001-provider-abstraction | **Date**: 2026-10-02

## Prerequisites

- JDK 21+
- Maven 3.9+
- （可选）DeepSeek 或 Kimi 的 API key（没有也能用 Mock Provider 跑通）

## Scenario 1: Mock Provider 全链路验证（无需 API key）

验证 ProviderService 能正常初始化、调用 Mock Provider 并写入审计记录。

### Setup

```bash
# 从项目根目录构建
mvn clean package -DskipTests

# 使用 mock profile 启动（不需要真实 API key）
java -jar oryxos-boot/target/oryxos-boot-*.jar --spring.profiles.active=mock
```

### Validation

```bash
# 1. 验证 Mock Provider 已注册
#    预期：ProviderService.listProviders() 包含 "mock"
#    通过启动日志确认："Registered providers: [mock]"

# 2. 验证 Mock Provider 调用
#    通过单元测试 / 集成测试触发 ProviderService.call("mock", null, messages, emptyList())
#    预期：返回 LlmResponse，content 为预配置的固定文本

# 3. 验证审计记录写入
#    查询 SQLite llm_calls 表
#    预期：存在一条 provider_name="mock", success=true 的记录
```

### Expected Outcome

- 启动无报错，Mock Provider 正常注册
- `ProviderService.call()` 返回固定文本响应
- `llm_calls` 表有对应审计记录（provider_name=mock, model=mock-model）

## Scenario 2: Mock Provider Function Calling 模拟

验证 OryxTool 能转换为 Function Calling 格式，Mock Provider 能返回 tool_call 响应。

### Setup

同 Scenario 1，使用 mock profile。

### Validation

```bash
# 1. 注册一个测试用 OryxTool（如 get_weather）
# 2. 调用 ProviderService.call("mock", null, messages, [get_weather_tool])
# 3. 预期：LlmResponse.toolCalls 包含一个 ToolCall(name="get_weather", arguments="{...}")
# 4. 验证 tool_call 的 name 和 arguments 可被正确解析
```

### Expected Outcome

- `FunctionCallingAdapter` 正确将 `OryxTool` 转换为 Spring AI `FunctionCallback`
- Mock Provider 返回包含 `tool_call` 的响应
- 响应中的 `ToolCall` 对象字段完整可解析

## Scenario 3: 真实 Provider 调用（DeepSeek）

验证与真实 LLM API 的端到端连通性。

### Setup

```bash
# 设置环境变量
export DEEPSEEK_API_KEY=sk-your-actual-key

# 启动，使用默认 profile（或包含 deepseek 配置的 profile）
java -jar oryxos-boot/target/oryxos-boot-*.jar
```

### Validation

```bash
# 1. 验证启动日志："Registered providers: [..., deepseek]"
# 2. 调用 ProviderService.call("deepseek", null, [UserMessage("你好")], emptyList())
# 3. 预期：LlmResponse.content 包含 DeepSeek 的有效中文回复
# 4. 查询 llm_calls 表：provider_name="deepseek", success=true, input_tokens > 0
```

### Expected Outcome

- DeepSeek Provider 注册成功
- LLM 调用返回有效文本（< 5s，排除网络延迟）
- 审计记录完整（含 token usage）

## Scenario 4: 多 Provider 并存路由

验证同时配置多个 Provider 时路由正确性。

### Setup

```bash
export DEEPSEEK_API_KEY=sk-xxx
export KIMI_API_KEY=sk-yyy

# 启动，application.yaml 同时配置 deepseek、kimi、mock 三个 provider
java -jar oryxos-boot/target/oryxos-boot-*.jar
```

### Validation

```bash
# 1. ProviderService.listProviders() 返回 ["deepseek", "kimi", "mock"]
# 2. call("deepseek", ...) 路由到 DeepSeek API
# 3. call("kimi", ...) 路由到 Kimi API
# 4. call("mock", ...) 返回固定文本
# 5. call("nonexistent", ...) 抛出 ProviderNotFoundException
```

### Expected Outcome

- 三个 Provider 互不干扰
- 按 provider name 正确路由
- 不存在的 name 报明确错误

## Scenario 5: 配置校验——缺失 API key

验证配置缺失时的 fail-fast 行为。

### Setup

```bash
# 故意不设置环境变量
unset DEEPSEEK_API_KEY

# 启动，application.yaml 中配置了 deepseek（api-key: ${DEEPSEEK_API_KEY}）
java -jar oryxos-boot/target/oryxos-boot-*.jar
```

### Expected Outcome

- 启动失败
- 错误信息明确指出：`Provider 'deepseek' API key is missing. Set environment variable DEEPSEEK_API_KEY`
- 不是 Spring 的通用 `PlaceholderResolutionException`，而是业务层面的友好错误

## Scenario 6: 错误透传

验证 LLM 调用失败时的错误处理。

### Validation

```bash
# 1. 配置一个 base-url 指向不存在域名的 Provider
# 2. 调用 ProviderService.call(...)
# 3. 预期：抛出 ProviderCallException，包含 provider name 和原始错误信息
# 4. 查询 llm_calls 表：success=false, error_message 非空
```

### Expected Outcome

- `ProviderCallException` 包含足够的上下文信息
- 不做重试，直接抛出
- 审计表记录失败详情
