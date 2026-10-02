# Research: Provider 抽象——对接大模型的统一入口

**Feature**: 001-provider-abstraction | **Date**: 2026-10-02

## 1. Spring AI Alibaba 多 Provider 显式映射最佳实践

**Decision**: 不使用 Spring AI 的自动 `ChatModel` Bean 注册，自行通过 `@ConfigurationProperties` 读取 `oryxos.providers` 配置列表，手动创建每个 `ChatModel` 实例并放入 `Map<String, ChatModel>`。

**Rationale**:
- Spring AI Alibaba 的默认自动配置为每种 connector 类型创建一个 `ChatModel` Bean（如 `OpenAiChatModel`）。当同时配置两个 OpenAI-compatible Provider（如 DeepSeek 和 Kimi，都走 OpenAI 协议）时，会出现同类型多 Bean 冲突。
- 自行创建 `ChatModel` 实例可以完全控制映射关系，每个实例绑定唯一的 provider name。
- 配置驱动：`application.yaml` 中 `oryxos.providers` 列表声明每个 Provider 的 name、type（`openai` / `dashscope`）、model、api-key 占位符、base-url。`ProviderRegistrar` 启动时遍历配置、按 type 选择对应的 `ChatModel` 构造器、建立映射。

**Alternatives Considered**:
- `@Qualifier` + 手工 Bean 声明：每加一个 Provider 就要改 Java 配置类，不符合"配置驱动"。
- `BeanFactoryPostProcessor` 动态注册：增加 Spring 容器耦合，调试困难。

## 2. 禁用 Spring AI 自动装配（eager auto-config）

**Decision**: 在 `application.yaml` 中显式排除所有 Spring AI Alibaba 的自动配置类（如 `DashScopeAutoConfiguration`、`OpenAiAutoConfiguration`），由 `oryxos-provider` 的 `ProviderAutoConfiguration` 接管 ChatModel 创建。

**Rationale**:
- Spring AI 的自动装配会在启动时尝试初始化所有声明了依赖的 connector，如果某个 Provider 没有配 API key 会阻断启动（FR-009）。
- 显式排除后，只有 `oryxos.providers` 中实际配置的 Provider 才会被实例化。
- Mock Provider 不走 Spring AI connector，由 `MockChatModel`（实现 `ChatModel` 接口）直接返回固定响应。

**Alternatives Considered**:
- `@ConditionalOnProperty` 逐个控制：Spring AI 内部的 auto-config 类不一定都提供 property 开关，不够可靠。
- `spring.autoconfigure.exclude` 全局排除：这正是我们选择的方式，可靠且显式。

## 3. Function Calling 适配策略

**Decision**: `FunctionCallingAdapter` 将 `OryxTool` 列表转换为 Spring AI 的 `FunctionCallback` 列表，在调 `ChatModel.call(Prompt)` 时通过 `ChatOptions` 传入。LLM 返回的 `tool_call` 由调用方（未来的 ReAct 循环）解析执行，`ProviderService` 只负责传递。

**Rationale**:
- Spring AI 提供 `FunctionCallback` / `FunctionCallbackWrapper` 机制生成 JSON Schema 并序列化到 LLM 请求中（FR-004）。
- 必须禁用 Spring AI 的自动 tool 执行（FR-005，宪法原则 II）：不使用 `ChatClient` 的 fluent API（它会自动执行 tool），而是直接用 `ChatModel.call(new Prompt(messages, options))`。
- `ProviderService.call()` 返回的 `LlmResponse` 包含 `List<ToolCall>`（tool name + arguments JSON），由上层决定是否执行。

**Alternatives Considered**:
- 自己拼 JSON Schema：Spring AI 已经做好了这件事，重复实现违反技术方案决策二。
- 用 `ChatClient` 但关闭 auto-execute：`ChatClient` 的 API 设计倾向自动执行，关闭路径不稳定，直接用 `ChatModel` 更安全。

## 4. 审计表 `llm_calls` 写入时机

**Decision**: 在 `DefaultProviderService.call()` 内部，LLM 调用返回后立即通过 `LlmCallAuditor` 写入 `llm_calls` 表。写入与业务调用在同一虚拟线程内同步完成。

**Rationale**:
- 宪法原则 V（NON-NEGOTIABLE）：审计写入不可省，Day One 就落库。
- 同步写入简单可靠（宪法原则 VII），虚拟线程下 SQLite 写入延迟可忽略。
- 记录字段：id、provider_name、model、input_tokens、output_tokens、total_tokens、duration_ms、timestamp、success（boolean）、error_message（nullable）。

**Alternatives Considered**:
- 异步事件写入（`ApplicationEvent`）：引入异步违反原则 VII，且增加丢失风险。
- 只写日志：违反原则 V，日志反解析代价高。

## 5. Mock Provider 实现策略

**Decision**: `MockChatModel` 实现 Spring AI 的 `ChatModel` 接口，固定返回可配置的文本响应，并支持模拟 Function Calling（返回 `tool_call` 响应）。通过 `application-mock.yaml` 激活，provider name 为 `mock`。

**Rationale**:
- FR-010 要求内置 Mock Provider 以支持无 API key 的全链路验证和 CI 测试。
- 实现 `ChatModel` 接口而非在 `ProviderService` 层 mock，这样 Mock Provider 的行为路径与真实 Provider 完全一致（包括 Function Calling 适配、审计写入）。
- Mock 模式的 Function Calling 模拟：配置指定当特定 tool name 出现在可用工具中时，返回对应的 `tool_call` 响应，参数为预设 JSON。

**Alternatives Considered**:
- 测试中用 Mockito mock `ChatModel`：无法支持集成测试和开发者手动跑通全链路。
- Mock Server（WireMock）：增加外部依赖，CI 复杂度高。

## 6. API Key 安全加载与校验

**Decision**: `application.yaml` 中 API key 统一使用 `${ENV_VAR:}` 占位符。`ProviderRegistrar` 在构建映射表时校验每个配置了的 Provider 的 API key 非空非空白字符串；校验失败时抛出 `IllegalStateException` 并给出明确错误信息（指出缺失的配置项和对应环境变量名）。Mock Provider 不需要 API key。

**Rationale**:
- FR-006、FR-008、宪法原则 VI 共同要求：key 不明文、配置缺失清晰报错。
- Spring Boot 原生支持 `${ENV_VAR}` 解析，无需额外实现。
- 日志中 API key 的脱敏：`ProviderProperties` 的 `toString()` 方法 MUST NOT 输出 apiKey 字段；Logback 配置中不记录请求 headers。

**Alternatives Considered**:
- 启动后首次调用时才校验：延迟暴露问题，不如 fail-fast。
- 加密存储 key：核心阶段过度设计，环境变量已满足安全需求。

## 7. 错误透传策略

**Decision**: `ProviderService.call()` 在 LLM 调用失败时（网络超时、HTTP 5xx、响应解析异常）直接抛出包装后的 `ProviderCallException`（包含 provider name、model、原始错误信息），不做重试。审计表中 `success=false` + `error_message` 记录失败详情。

**Rationale**:
- FR-011 + spec clarification：Provider 层不重试，错误透传给调用方（未来的 ReAct 循环），重试策略留给扩展阶段。
- 包装为统一异常类型，上层不需要区分是 `HttpTimeoutException` 还是 `RestClientException`。

**Alternatives Considered**:
- 返回 `Optional` 或 `Result` 类型：增加调用方复杂度，且"错误"不应被静默吞掉。
- 按错误类型分类重试：clarification 明确不做，留给扩展阶段。
