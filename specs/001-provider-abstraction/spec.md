# Feature Specification: Provider 抽象——对接大模型的统一入口

**Feature Branch**: `dev/first-task`

**Created**: 2026-10-02

**Status**: Draft

**Input**: US-1 — Provider 抽象（对接 LLM）。让 OryxOS 能调用任意主流 LLM，Agent 不感知具体调的是哪家模型。LLM 调用复杂度由 Spring AI Alibaba 吸收，OryxOS 只做一层薄包装。

**Source**:
- 需求文档 `docs/DemandAnalysis.md` §5.3（核心能力一：对接 LLM）
- 技术方案 `docs/TechnicalSolution.md` §3（Provider 抽象）
- AI 编程指南 `docs/AiProgrammingGuide.md` US-1

## Clarifications

### Session 2026-10-02

- Q: 系统是否应该内置一个 Mock Provider，让开发者在没有任何真实 LLM API key 的情况下也能跑通全链路？ → A: 内置 Mock Provider，返回固定文本响应，支持 Function Calling 模拟
- Q: 当 LLM Provider API 调用失败（网络超时、5xx 错误）时，ProviderService 应该采用什么重试策略？ → A: 不重试，直接将错误信息透传给调用方（ReAct 循环），重试策略留给扩展阶段

## User Scenarios & Testing

### User Story 1 - 单 Provider 对话调用 (Priority: P1)

企业开发者在 `application.yaml` 中配置一个 LLM Provider（如 DeepSeek），启动 OryxOS 后，通过 ReAct 循环向该 Provider 发送对话请求，获得正确的 LLM 响应。开发者无需关心底层是哪家 LLM 的 API 协议。

**Why this priority**: 这是整个 Agent OS 的最基础能力——没有 LLM 调用，所有后续能力（ReAct、Memory、Tool）都无法运行。单 Provider 调通是最短验证路径。

**Independent Test**: 配置一个 DeepSeek Provider，调用 `ProviderService.call(profile, prompt)` 传入简单 prompt，验证返回有效的 LLM 文本响应。

**Acceptance Scenarios**:

1. **Given** `application.yaml` 配置了 DeepSeek 的 api-key 和 base-url, **When** 调用 `ProviderService` 发送 "你好", **Then** 返回 DeepSeek 的有效文本响应
2. **Given** `application.yaml` 配置了 DeepSeek Provider, **When** OryxOS 启动, **Then** `ProviderService` 的 provider 映射表中包含 `deepseek` 条目
3. **Given** 一个 Profile 指定 `provider: deepseek`, **When** ReAct 循环通过该 Profile 调 LLM, **Then** 请求路由到 DeepSeek 的 ChatModel 而非其他 Provider

---

### User Story 2 - 多 Provider 并存与切换 (Priority: P2)

企业开发者同时配置多个 LLM Provider（如 DeepSeek 和 Kimi），不同 Agent Profile 引用不同 Provider，运行时根据 Profile 配置路由到正确的 LLM，互不干扰。

**Why this priority**: 多 Provider 并存是企业场景的刚需（不同任务用不同模型），但优先级低于单 Provider 调通。

**Independent Test**: 配置 DeepSeek 和 Kimi 两个 Provider，创建两个 Profile 分别引用，验证调用时路由到各自正确的 ChatModel。

**Acceptance Scenarios**:

1. **Given** `application.yaml` 配置了 deepseek 和 kimi 两个 Provider, **When** OryxOS 启动, **Then** `ProviderService` 映射表中同时包含 `deepseek` 和 `kimi` 两个条目
2. **Given** Profile A 指定 `provider: deepseek`、Profile B 指定 `provider: kimi`, **When** 分别通过两个 Profile 调 LLM, **Then** 请求各自路由到正确的 ChatModel
3. **Given** 配置了多个 Provider, **When** 某个 Profile 引用了不存在的 provider name, **Then** 启动时报告明确错误而非静默失败

---

### User Story 3 - Function Calling 格式适配 (Priority: P2)

OryxOS 内部的 `OryxTool` 抽象能被正确转换为 LLM 能理解的 Function Calling 格式，且转换过程完全利用 Spring AI 已有能力，不重复实现各家 LLM 的协议差异。

**Why this priority**: Function Calling 是 ReAct 循环调工具的前提，但其格式转换依赖 Spring AI 已有能力，实现成本低。

**Independent Test**: 注册一个 OryxTool，验证 Spring AI 能正确生成其 JSON Schema 并在 LLM 调用时携带 tools 参数。

**Acceptance Scenarios**:

1. **Given** 注册了一个带参数的 OryxTool, **When** 组装 LLM 调用请求, **Then** 请求中包含该 Tool 的正确 JSON Schema（name、description、parameters）
2. **Given** LLM 返回了 tool_call 响应, **When** 解析响应, **Then** 能正确提取 tool name 和 arguments，交由 ReAct 循环处理（不触发 Spring AI 的自动执行）

---

### User Story 4 - API Key 安全加载 (Priority: P3)

API key 通过环境变量或配置文件加载，不会出现在代码、日志或提交历史中。配置缺失时给出明确的启动失败提示。

**Why this priority**: 安全是地基（宪法原则 VI），但属于配置层面，不影响核心调用链路验证。

**Independent Test**: 通过环境变量设置 API key，启动 OryxOS 验证 Provider 正常工作；故意不配 API key 验证启动报错信息。

**Acceptance Scenarios**:

1. **Given** API key 通过环境变量设置, **When** OryxOS 启动, **Then** Provider 正常初始化且 key 不出现在日志中
2. **Given** 未配置某 Provider 的 API key, **When** OryxOS 启动, **Then** 报告明确的错误信息指出缺失的配置项
3. **Given** `application.yaml` 中 API key 使用 `${ENV_VAR}` 占位符, **When** 对应环境变量存在, **Then** 正确解析并初始化 Provider

---

### Edge Cases

- 当 LLM Provider 的 API 返回网络超时或 5xx 错误时，调用方收到明确的错误信息而非未处理异常
- 当配置文件中出现重复的 provider name 时，启动时报告冲突而非静默覆盖
- 当 API key 格式无效（如空字符串）时，在首次调用前给出校验失败提示
- 当 Spring AI Alibaba 的自动装配被禁用后，不存在 API key 的 Provider 不会阻断 OryxOS 启动
- 使用 Mock Provider 时，系统返回固定文本响应并能模拟 Function Calling 返回 tool_call，以支持全链路验证

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 提供统一的 `ProviderService` 抽象，对调用方屏蔽不同 LLM 厂商的 API 差异
- **FR-002**: 系统 MUST 维护显式的 provider name → ChatModel 映射表，MUST NOT 依赖扫描 Spring 容器中的 ChatModel Bean 类型来区分 Provider
- **FR-003**: 系统 MUST 支持通过 `application.yaml` 配置多个 Provider 实例，每个实例包含 provider name、模型名、API key、可选 base URL
- **FR-004**: 系统 MUST 将 OryxOS 的 `OryxTool` 抽象转换为 Spring AI 的 Function Calling 格式，仅使用 Spring AI 的 JSON Schema 生成能力
- **FR-005**: 系统 MUST 禁用 Spring AI 的自动 tool 执行，tool call 的解析和执行由 OryxOS 自行控制
- **FR-006**: 系统 MUST 支持通过环境变量加载 API key，MUST NOT 将 API key 明文写入日志或提交历史
- **FR-007**: 系统 MUST 在每次 LLM 调用时记录 token 使用量、Provider name、模型名到审计表 `llm_calls`
- **FR-008**: 系统 MUST 在配置缺失或无效时给出明确的错误信息，而非静默失败或抛出未处理异常
- **FR-009**: 系统 MUST 禁用 Spring AI Alibaba 的 eager 模型自动装配（如 `DashScopeAutoConfiguration`），避免无 API key 的 Provider 阻断启动
- **FR-010**: 系统 MUST 内置一个 Mock Provider，返回固定文本响应并支持 Function Calling 模拟，使开发者在无真实 API key 时也能跑通全链路和 CI 测试
- **FR-011**: Provider 调用失败时（网络超时、5xx 错误），系统 MUST 将错误信息直接透传给调用方，MUST NOT 在 Provider 层内置重试逻辑，重试策略留给扩展阶段

### Key Entities

- **Provider**: 一个 LLM API 服务的抽象表示，包含 provider name（唯一标识）、模型名、API endpoint、认证信息
- **Profile**: Agent 的运行时配置，引用一个 Provider name 来决定 LLM 调用路由
- **ProviderService**: 统一管理所有 Provider 的门面，接收 Profile 和 Prompt 完成 LLM 调用
- **OryxTool**: 工具的内部抽象，需要被适配为 LLM 能理解的 Function Calling 格式
- **LLM Call Record**: 每次 LLM 调用的审计记录，包含 token 用量、Provider、模型、时间戳
- **Mock Provider**: 内置的测试用 Provider，无需 API key，返回固定文本并支持模拟 Function Calling

## Success Criteria

### Measurable Outcomes

- **SC-001**: 开发者配置一个 Provider 后，首次 LLM 调用在 5 秒内返回有效响应（排除网络延迟）
- **SC-002**: 系统支持同时配置至少 3 个不同的 LLM Provider，各 Provider 间路由零错误
- **SC-003**: 切换 Agent 使用的 Provider 只需修改 Profile 中的 provider name，无需改动任何代码
- **SC-004**: 每次 LLM 调用的 token 用量和 Provider 信息 100% 写入审计记录
- **SC-005**: API key 在任何日志输出和错误信息中均不可见

## Assumptions

- 企业已具备至少一个 LLM Provider 的 API key（如 DeepSeek 或 Kimi）
- 网络环境允许访问 LLM Provider 的 API endpoint
- Spring AI Alibaba 已提供 DeepSeek、通义、Kimi 等主流国产 LLM 的 connector，OryxOS 不需要自行实现协议适配
- 核心阶段不做 Provider fallback、hedge racing 和成本聚合看板，这些放扩展阶段
- US-1 完成后没有独立可演示 Demo，需要等 US-2（ReAct 循环）完成后合跑「查天气」Demo
- Maven 多模块骨架（oryxos-core、oryxos-provider、oryxos-boot 等）在本 US 中一并搭建
