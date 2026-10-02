# Data Model: Provider 抽象——对接大模型的统一入口

**Feature**: 001-provider-abstraction | **Date**: 2026-10-02

## Entities

### 1. Profile（核心抽象，定义在 `oryxos-core`）

Agent 的运行时配置。US-1 阶段只需其中与 Provider 相关的字段，其余字段在后续 US 中扩展。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| name | String | NOT NULL, UNIQUE | Agent 标识名 |
| provider | String | NOT NULL | 引用的 provider name，必须在 ProviderService 映射表中存在 |
| model | String | NULLABLE | 可选的模型覆盖（如果不指定，使用 Provider 配置中的默认模型） |
| maxHistoryTurns | int | DEFAULT 20 | 上下文保留轮数（US-2 使用） |
| maxIterations | int | DEFAULT 10 | ReAct 最大迭代次数（US-2 使用） |

> Profile 在 US-1 阶段不持久化到数据库，只作为内存数据结构。后续从 AGENT.md frontmatter 派生。

### 2. Message（核心抽象，定义在 `oryxos-core`）

对话消息。US-1 阶段需要此结构以组装 LLM 调用的 Prompt。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| role | MessageRole | NOT NULL | SYSTEM / USER / ASSISTANT / TOOL |
| content | String | NULLABLE | 文本内容（tool_call 消息可为空） |
| toolCalls | List\<ToolCall\> | NULLABLE | LLM 返回的工具调用请求列表 |
| toolCallId | String | NULLABLE | 工具执行结果消息的关联 ID |
| name | String | NULLABLE | 工具名称（TOOL 角色消息） |

### 3. ToolCall（值对象，定义在 `oryxos-core`）

LLM 返回的工具调用请求。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | String | NOT NULL | 调用 ID（LLM 生成） |
| name | String | NOT NULL | 工具名称 |
| arguments | String | NOT NULL | 参数 JSON 字符串 |

### 4. LlmResponse（值对象，定义在 `oryxos-core`）

`ProviderService.call()` 的返回结果。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| content | String | NULLABLE | LLM 返回的文本内容 |
| toolCalls | List\<ToolCall\> | NULLABLE | 如果 LLM 请求调用工具 |
| tokenUsage | TokenUsage | NOT NULL | token 用量统计 |
| finishReason | String | NULLABLE | 结束原因（stop / tool_calls 等） |

### 5. TokenUsage（值对象，定义在 `oryxos-core`）

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| inputTokens | long | >= 0 | 输入 token 数 |
| outputTokens | long | >= 0 | 输出 token 数 |
| totalTokens | long | >= 0 | 总 token 数 |

### 6. OryxTool（接口，定义在 `oryxos-core`）

工具的内部抽象。US-1 阶段只定义接口，不实现具体工具（留给 US-2）。

| Method | Return | Description |
|--------|--------|-------------|
| getName() | String | 工具唯一名称 |
| getDescription() | String | 工具描述（进入 JSON Schema） |
| getParameterSchema() | Class\<?\> | 参数 POJO 类型（Spring AI 据此生成 JSON Schema） |
| execute(String argsJson) | String | 执行工具，返回文本结果 |

### 7. ProviderConfig（配置值对象，定义在 `oryxos-provider`）

从 `application.yaml` 绑定的单个 Provider 配置。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| name | String | NOT NULL, UNIQUE | provider 唯一标识 |
| type | String | NOT NULL | connector 类型：`openai` / `dashscope` / `mock` |
| model | String | NOT NULL | 默认模型名 |
| apiKey | String | NULLABLE（mock 可为空） | API key，通过 `${ENV_VAR}` 解析 |
| baseUrl | String | NULLABLE | 自定义 API endpoint |

**Validation Rules**:
- name 不能重复，启动时检测重复报错（FR-008）
- 非 mock 类型的 apiKey 不能为空或空白字符串
- type 必须是已知的 connector 类型之一

### 8. LlmCallRecord（审计实体，定义在 `oryxos-storage`，持久化到 `llm_calls` 表）

| Field | Type | DB Column | Constraints | Description |
|-------|------|-----------|-------------|-------------|
| id | Long | id | PK, AUTO_INCREMENT | 主键 |
| providerName | String | provider_name | NOT NULL | Provider 名称 |
| model | String | model | NOT NULL | 使用的模型 |
| inputTokens | long | input_tokens | NOT NULL, DEFAULT 0 | 输入 token 数 |
| outputTokens | long | output_tokens | NOT NULL, DEFAULT 0 | 输出 token 数 |
| totalTokens | long | total_tokens | NOT NULL, DEFAULT 0 | 总 token 数 |
| durationMs | long | duration_ms | NOT NULL | 调用耗时（毫秒） |
| success | boolean | success | NOT NULL | 是否成功 |
| errorMessage | String | error_message | NULLABLE | 失败时的错误信息 |
| createdAt | Instant | created_at | NOT NULL | 记录时间戳 |

**Flyway Migration** (`V1__create_llm_calls.sql`):

```sql
CREATE TABLE llm_calls (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_name TEXT    NOT NULL,
    model         TEXT    NOT NULL,
    input_tokens  INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens  INTEGER NOT NULL DEFAULT 0,
    duration_ms   INTEGER NOT NULL,
    success       INTEGER NOT NULL,
    error_message TEXT,
    created_at    TEXT    NOT NULL
);
```

## Entity Relationships

```text
Profile --[references]--> ProviderConfig.name (provider name 字符串引用)
ProviderService --[maintains]--> Map<String(provider name), ChatModel>
ProviderService.call() --[produces]--> LlmResponse
ProviderService.call() --[audits]--> LlmCallRecord (写入 llm_calls 表)
FunctionCallingAdapter --[converts]--> List<OryxTool> → List<FunctionCallback>
```

## State Transitions

无状态机——Provider 调用是无状态的请求-响应模型。`LlmCallRecord` 一旦写入不可变。
