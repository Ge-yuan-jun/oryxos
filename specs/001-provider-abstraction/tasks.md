# Tasks: Provider 抽象——对接大模型的统一入口

**Input**: Design documents from `/specs/001-provider-abstraction/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/provider-service-contract.md, quickstart.md

**Tests**: Spec 未显式要求 TDD，但 Mock Provider（FR-010）和 quickstart.md 验证场景隐含需要集成测试。按需生成关键验证测试。

**Organization**: Tasks grouped by user story (US1–US4) from spec.md, priority order P1→P2→P3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven 多模块骨架搭建、Spring Boot 基础配置、依赖声明

- [x] T001 Create/verify Maven multi-module project structure: parent `pom.xml` 声明 `oryxos-core`, `oryxos-provider`, `oryxos-storage`, `oryxos-boot` 四个子模块（其余模块可存在但 US-1 不涉及），统一管理 Spring Boot 3.x + Spring AI Alibaba + JDK 21 版本 in `/pom.xml`
- [x] T002 [P] Configure `oryxos-core/pom.xml`: 仅声明 Spring AI core 依赖（接口级别），不引入具体 connector
- [x] T003 [P] Configure `oryxos-provider/pom.xml`: 依赖 `oryxos-core` + Spring AI Alibaba OpenAI connector + DashScope connector
- [x] T004 [P] Configure `oryxos-storage/pom.xml`: 依赖 `oryxos-core` + Spring Data JPA + SQLite JDBC + Flyway
- [x] T005 [P] Configure `oryxos-boot/pom.xml`: 依赖 `oryxos-core` + `oryxos-provider` + `oryxos-storage`，Spring Boot Maven Plugin 打 fat JAR
- [x] T006 [P] Create `oryxos-boot/src/main/java/com/oryxos/boot/OryxOsApplication.java`: Spring Boot 主类，启用虚拟线程（`spring.threads.virtual.enabled=true`）
- [x] T007 [P] Create `oryxos-boot/src/main/resources/application.yaml`: 基础配置骨架，包含 `spring.datasource`（SQLite）、`spring.jpa`、`spring.flyway`、`spring.autoconfigure.exclude`（排除 Spring AI 自动装配类）

**Checkpoint**: `mvn clean compile` 通过，四个模块编译成功，Spring Boot 应用可启动（无 Provider 配置时跳过 Provider 初始化）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心抽象和持久化基础，所有 User Story 依赖这些类型

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T008 [P] Create `MessageRole` enum in `oryxos-core/src/main/java/com/oryxos/core/message/MessageRole.java`: 四个值 SYSTEM, USER, ASSISTANT, TOOL
- [x] T009 [P] Create `ToolCall` value object in `oryxos-core/src/main/java/com/oryxos/core/message/ToolCall.java`: fields id (String, NOT NULL), name (String, NOT NULL), arguments (String, NOT NULL)
- [x] T010 [P] Create `Message` data class in `oryxos-core/src/main/java/com/oryxos/core/message/Message.java`: fields role (MessageRole, NOT NULL), content (String, NULLABLE), toolCalls (List\<ToolCall\>, NULLABLE), toolCallId (String, NULLABLE), name (String, NULLABLE); provide static factory methods `userMessage(content)`, `systemMessage(content)`, `assistantMessage(content, toolCalls)`, `toolMessage(toolCallId, name, content)`
- [x] T011 [P] Create `TokenUsage` value object in `oryxos-core/src/main/java/com/oryxos/core/provider/TokenUsage.java`: fields inputTokens (long, >= 0), outputTokens (long, >= 0), totalTokens (long, >= 0)
- [x] T012 [P] Create `LlmResponse` value object in `oryxos-core/src/main/java/com/oryxos/core/provider/LlmResponse.java`: fields content (String, NULLABLE), toolCalls (List\<ToolCall\>, NULLABLE), tokenUsage (TokenUsage, NOT NULL), finishReason (String, NULLABLE); provide `hasToolCalls()` convenience method
- [x] T013 [P] Create `OryxTool` interface in `oryxos-core/src/main/java/com/oryxos/core/tool/OryxTool.java`: methods getName() → String, getDescription() → String, getParameterSchema() → Class\<?\>, execute(String argsJson) → String
- [x] T014 [P] Create `Profile` data class in `oryxos-core/src/main/java/com/oryxos/core/profile/Profile.java`: fields name (String, NOT NULL, UNIQUE), provider (String, NOT NULL), model (String, NULLABLE), maxHistoryTurns (int, DEFAULT 20), maxIterations (int, DEFAULT 10)
- [x] T015 [P] Create `ProviderService` interface in `oryxos-core/src/main/java/com/oryxos/core/provider/ProviderService.java`: methods `call(String providerName, String model, List<Message> messages, List<OryxTool> tools) → LlmResponse`, `listProviders() → List<String>`, `hasProvider(String providerName) → boolean` per contracts/provider-service-contract.md
- [x] T016 [P] Create `ProviderNotFoundException` in `oryxos-core/src/main/java/com/oryxos/core/provider/ProviderNotFoundException.java`: extends RuntimeException, includes providerName field
- [x] T017 [P] Create `ProviderCallException` in `oryxos-core/src/main/java/com/oryxos/core/provider/ProviderCallException.java`: extends RuntimeException, includes providerName, model, and cause fields
- [x] T018 [P] Create `LlmCallRecord` JPA entity in `oryxos-storage/src/main/java/com/oryxos/storage/entity/LlmCallRecord.java`: fields id (Long, PK AUTO_INCREMENT), providerName (String, NOT NULL), model (String, NOT NULL), inputTokens (long, NOT NULL DEFAULT 0), outputTokens (long, NOT NULL DEFAULT 0), totalTokens (long, NOT NULL DEFAULT 0), durationMs (long, NOT NULL), success (boolean, NOT NULL), errorMessage (String, NULLABLE), createdAt (Instant, NOT NULL); map to table `llm_calls`
- [x] T019 [P] Create `LlmCallRepository` in `oryxos-storage/src/main/java/com/oryxos/storage/repository/LlmCallRepository.java`: extends JpaRepository\<LlmCallRecord, Long\>
- [x] T020 Create Flyway migration `oryxos-storage/src/main/resources/db/migration/sqlite/V1__create_llm_calls.sql`: CREATE TABLE llm_calls with columns id INTEGER PRIMARY KEY AUTOINCREMENT, provider_name TEXT NOT NULL, model TEXT NOT NULL, input_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0, total_tokens INTEGER NOT NULL DEFAULT 0, duration_ms INTEGER NOT NULL, success INTEGER NOT NULL, error_message TEXT, created_at TEXT NOT NULL

**Checkpoint**: 所有核心类型编译通过，Flyway 迁移在启动时自动创建 `llm_calls` 表。`ProviderService` 接口已定义但无实现。

---

## Phase 3: User Story 1 — 单 Provider 对话调用 (Priority: P1) 🎯 MVP

**Goal**: 配置一个 LLM Provider（如 DeepSeek），通过 `ProviderService.call()` 发送对话请求并获得有效 LLM 响应，每次调用写入审计记录。

**Independent Test**: 配置一个 DeepSeek Provider，调用 `ProviderService.call("deepseek", null, [userMessage("你好")], [])` 验证返回有效文本响应，查询 `llm_calls` 表确认审计记录存在。

### Implementation for User Story 1

- [x] T021 [US1] Create `ProviderProperties` configuration binding in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderProperties.java`: `@ConfigurationProperties("oryxos")` 绑定 `providers` 列表，每个元素包含 name (String, NOT NULL, UNIQUE), type (String, NOT NULL, enum: openai/dashscope/mock), model (String, NOT NULL), apiKey (String, NULLABLE for mock), baseUrl (String, NULLABLE); `toString()` MUST NOT output apiKey
- [x] T022 [US1] Create `LlmCallAuditor` in `oryxos-provider/src/main/java/com/oryxos/provider/LlmCallAuditor.java`: 接收 providerName, model, TokenUsage, durationMs, success, errorMessage 参数，构建 `LlmCallRecord` 并通过 `LlmCallRepository.save()` 同步写入；依赖 `oryxos-storage` 的 repository
- [x] T023 [US1] Create `ProviderRegistrar` in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderRegistrar.java`: `@Component` 在 `@PostConstruct` 中遍历 `ProviderProperties.providers`，按 type 创建对应的 `ChatModel` 实例（openai → `OpenAiChatModel`，dashscope → `DashScopeChatModel`，mock → defer to T030），构建 `Map<String, ChatModel>` 映射表；启动时校验：name 不重复（重复则抛 IllegalStateException 含重复 name）、非 mock 的 apiKey 非空非空白（缺失则抛 IllegalStateException 含 provider name 和环境变量提示）、type 是已知类型
- [x] T024 [US1] Create `DefaultProviderService` in `oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java`: 实现 `ProviderService` 接口；`call()` 方法从 `ProviderRegistrar` 的映射表中按 providerName 查找 `ChatModel`（未找到抛 `ProviderNotFoundException`）；将 `List<Message>` 转换为 Spring AI 的 `Prompt` 对象（Message → Spring AI `AbstractMessage` 子类映射）；调用 `chatModel.call(prompt)` 获取响应；提取 content、tool calls、token usage 构建 `LlmResponse`；记录耗时，通过 `LlmCallAuditor` 写审计；异常时捕获并包装为 `ProviderCallException`，同样写审计（success=false）
- [x] T025 [US1] Create `ProviderAutoConfiguration` in `oryxos-provider/src/main/java/com/oryxos/provider/ProviderAutoConfiguration.java`: `@Configuration` + `@EnableConfigurationProperties(ProviderProperties.class)` + `@ComponentScan` 扫描 provider 包；在 `oryxos-provider/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册
- [x] T026 [US1] Add provider configuration to `oryxos-boot/src/main/resources/application.yaml`: 添加 `oryxos.providers` 配置示例（至少包含一个 openai type 的 provider，api-key 使用 `${DEEPSEEK_API_KEY:}` 占位符，base-url 配置 DeepSeek endpoint）；确保 `spring.autoconfigure.exclude` 包含 Spring AI 的自动配置类（如 `org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration`）
- [x] T027 [US1] Add Flyway configuration to `oryxos-boot/src/main/resources/application.yaml`: 配置 `spring.flyway.locations=classpath:db/migration/sqlite`，确保启动时自动执行迁移

**Checkpoint**: 设置 `DEEPSEEK_API_KEY` 环境变量后 `mvn clean package && java -jar oryxos-boot/target/oryxos-boot-*.jar` 启动成功，日志输出 "Registered providers: [deepseek]"，`llm_calls` 表已创建。可通过编程方式调用 `ProviderService.call("deepseek", null, messages, [])` 获得响应。

---

## Phase 4: User Story 2 — 多 Provider 并存与切换 (Priority: P2)

**Goal**: 同时配置多个 LLM Provider（如 DeepSeek + Kimi），不同 Profile 引用不同 Provider，运行时按 provider name 路由到正确的 ChatModel。

**Independent Test**: 配置 deepseek 和 kimi 两个 Provider，分别调用 `call("deepseek", ...)` 和 `call("kimi", ...)`，验证路由正确且各自有独立的审计记录；调用 `call("nonexistent", ...)` 验证抛出 `ProviderNotFoundException`。

### Implementation for User Story 2

- [x] T028 [US2] Extend `oryxos-boot/src/main/resources/application.yaml` provider configuration: 添加第二个 provider 配置（如 kimi，type: openai, api-key: `${KIMI_API_KEY:}`, base-url: Kimi endpoint）和 mock provider（type: mock, 无需 api-key）
- [x] T029 [US2] Add duplicate provider name detection in `ProviderRegistrar` (if not already covered by T023): 确保当 `oryxos.providers` 列表中出现重复 name 时，启动失败并输出错误信息 "Duplicate provider name: '{name}'"；同时验证 `listProviders()` 返回所有已注册 name，`hasProvider()` 正确判断

**Checkpoint**: 同时设置 `DEEPSEEK_API_KEY` 和 `KIMI_API_KEY` 后启动，日志输出 "Registered providers: [deepseek, kimi, mock]"。分别调用三个 provider 均正常工作且互不干扰。

---

## Phase 5: User Story 3 — Function Calling 格式适配 (Priority: P2)

**Goal**: `OryxTool` 列表能被正确转换为 Spring AI 的 Function Calling 格式，LLM 调用时携带 tools 参数，返回的 tool_call 能被正确解析为 `ToolCall` 对象。禁用 Spring AI 自动 tool 执行。

**Independent Test**: 创建一个测试用 `OryxTool`（如 get_weather），调用 `ProviderService.call(provider, null, messages, [get_weather])` 验证请求中包含 tool JSON Schema，LLM 返回的 `tool_call` 被正确提取到 `LlmResponse.toolCalls`。

### Implementation for User Story 3

- [x] T030 [US3] Create `FunctionCallingAdapter` in `oryxos-provider/src/main/java/com/oryxos/provider/FunctionCallingAdapter.java`: 将 `List<OryxTool>` 转换为 Spring AI 的 `List<FunctionCallback>`；每个 `OryxTool` 映射为一个 `FunctionCallback`，使用 Spring AI 的 JSON Schema 生成能力（基于 `getParameterSchema()` 返回的 Class 生成参数 schema）；回调的 `apply()` 方法 MUST NOT 自动执行 tool——仅作占位以满足 `FunctionCallback` 接口，实际执行由上层 ReAct 循环控制
- [x] T031 [US3] Integrate `FunctionCallingAdapter` into `DefaultProviderService.call()`: 当 tools 列表非空时，通过 `FunctionCallingAdapter` 转换后设置到 `ChatOptions`（如 `OpenAiChatOptions.builder().withFunctions(...)` 或等效方式）传入 `Prompt`；解析 LLM 响应中的 tool_call（Spring AI 的 `AssistantMessage.getToolCalls()`），映射为 `ToolCall` 列表放入 `LlmResponse.toolCalls`

**Checkpoint**: 使用 Mock Provider 或真实 Provider，传入一个 OryxTool 调用 `ProviderService.call()`，验证 LLM 响应中的 `toolCalls` 字段包含正确的 tool name 和 arguments JSON。

---

## Phase 6: User Story 4 — API Key 安全加载 (Priority: P3)

**Goal**: API key 通过环境变量安全加载，不出现在代码/日志/提交历史中。配置缺失时给出明确的启动失败提示。

**Independent Test**: 设置环境变量验证 Provider 正常工作；不设置环境变量验证启动报错包含缺失配置项信息；检查日志输出不包含 API key。

### Implementation for User Story 4

- [x] T032 [US4] Add Logback configuration in `oryxos-boot/src/main/resources/logback-spring.xml`: 配置结构化 JSON 日志格式（生产环境）和可读格式（开发环境）；确保不记录请求 headers 或敏感配置值
- [x] T033 [US4] Verify API key masking in startup logs: 确保 `ProviderRegistrar` 的启动日志只输出 provider name 和 model，不输出 apiKey 或 baseUrl 中的敏感信息；`ProviderProperties.toString()` 对 apiKey 字段输出 "***" 而非实际值
- [x] T034 [US4] Verify empty/blank API key validation: 确保 `ProviderRegistrar` 对空字符串 `""` 和空白字符串 `"  "` 的 apiKey 也视为缺失，报告明确错误 "Provider '{name}' API key is blank or missing. Set environment variable {VAR_NAME}"

**Checkpoint**: 启动日志中无 API key 泄露；故意不配置环境变量时启动失败并输出友好错误信息。

---

## Phase 7: Mock Provider (FR-010, Edge Cases)

**Goal**: 内置 Mock Provider 支持无 API key 全链路验证和 CI 测试，包括 Function Calling 模拟。

**Independent Test**: 使用 `application-mock.yaml` 启动，调用 `ProviderService.call("mock", ...)` 验证返回固定文本；传入 tools 验证返回模拟的 tool_call。

### Implementation

- [x] T035 Create `MockChatModel` in `oryxos-provider/src/main/java/com/oryxos/provider/MockChatModel.java`: 实现 Spring AI `ChatModel` 接口；`call(Prompt)` 返回可配置的固定文本响应（默认 "This is a mock response from OryxOS Mock Provider."）；当 Prompt 中包含 FunctionCallback 且配置了 mock tool calls 时，返回包含 `tool_call` 的 `AssistantMessage`（tool name 取第一个可用 tool，arguments 为预设 JSON `{}`）；返回固定 `TokenUsage`（input=10, output=20, total=30）
- [x] T036 Integrate `MockChatModel` into `ProviderRegistrar`: 当 provider type 为 `mock` 时，创建 `MockChatModel` 实例（不需要 apiKey 校验）
- [x] T037 [P] Create `oryxos-boot/src/main/resources/application-mock.yaml`: 配置 `oryxos.providers` 仅包含 mock provider（name: mock, type: mock, model: mock-model），用于 CI 和开发者无 API key 场景

**Checkpoint**: `java -jar oryxos-boot-*.jar --spring.profiles.active=mock` 启动成功，调用 mock provider 返回固定响应且审计记录写入 `llm_calls` 表。

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 错误透传验证、edge case 覆盖、quickstart 场景验证

- [ ] T038 [P] Verify error passthrough (FR-011): 配置一个指向不存在域名的 provider，调用后确认抛出 `ProviderCallException` 且审计表记录 success=false, error_message 非空
- [ ] T039 [P] Verify Spring AI auto-config exclusion (FR-009): 确认 `spring.autoconfigure.exclude` 中包含所有 Spring AI 自动配置类，无 API key 的 provider 不会阻断启动（仅 `ProviderRegistrar` 的显式校验会报错）
- [ ] T040 [P] Verify API key not in logs (edge case): 搜索启动日志和调用日志，确认无 API key 泄露
- [ ] T041 Run quickstart.md Scenario 1-6 validation: 按 quickstart.md 中的 6 个场景依次验证（Mock 全链路、FC 模拟、真实 Provider、多 Provider 路由、配置校验、错误透传）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — core single-provider path
- **US2 (Phase 4)**: Depends on Phase 3 (T023 already handles multi-provider, but validation needs single-provider path working first)
- **US3 (Phase 5)**: Depends on Phase 3 (needs working `DefaultProviderService` to integrate FC adapter)
- **US4 (Phase 6)**: Depends on Phase 3 (needs working provider to verify log masking)
- **Mock Provider (Phase 7)**: Can start after Phase 2, parallel with US1 (MockChatModel is independent); integrate into ProviderRegistrar after T023
- **Polish (Phase 8)**: Depends on all previous phases

### User Story Dependencies

- **US1 (P1)**: After Foundational → MVP milestone
- **US2 (P2)**: After US1 → multi-provider routing adds on top of single-provider
- **US3 (P2)**: After US1 → FC adapter integrates into existing `call()` method
- **US4 (P3)**: After US1 → security hardening layer on top of working provider
- **Mock Provider**: After Foundational, parallel with US1; T036 integrates after T023

### Within Each User Story

- Configuration/properties before service implementation
- Service implementation before integration/validation
- Auditor before service (US1: T022 before T024)

### Parallel Opportunities

- **Phase 1**: T002, T003, T004, T005, T006, T007 all [P]
- **Phase 2**: T008–T020 all [P] (different files, no dependencies)
- **Phase 3**: T021, T022 [P] (both needed by T024 but independent of each other)
- **Phase 7**: T035, T037 [P]; Phase 7 can run parallel with Phase 3–5 (MockChatModel is self-contained)
- **Phase 8**: T038, T039, T040 all [P]

---

## Parallel Example: Phase 2 (Foundational)

```text
# All foundational types can be created in parallel (different files):
Task T008: MessageRole enum
Task T009: ToolCall value object
Task T010: Message data class
Task T011: TokenUsage value object
Task T012: LlmResponse value object
Task T013: OryxTool interface
Task T014: Profile data class
Task T015: ProviderService interface
Task T016: ProviderNotFoundException
Task T017: ProviderCallException
Task T018: LlmCallRecord entity
Task T019: LlmCallRepository
Task T020: Flyway migration
```

## Parallel Example: User Story 1

```text
# These can run in parallel:
Task T021: ProviderProperties (config binding)
Task T022: LlmCallAuditor (audit writer)

# Then sequentially:
Task T023: ProviderRegistrar (depends on T021)
Task T024: DefaultProviderService (depends on T022, T023)
Task T025: ProviderAutoConfiguration (depends on T024)
Task T026-T027: Boot configuration (depends on T025)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Maven skeleton + Boot config
2. Complete Phase 2: All core abstractions + audit table
3. Complete Phase 3: Single provider call works end-to-end
4. **STOP and VALIDATE**: Call `ProviderService.call("deepseek", null, messages, [])` → valid response + audit record
5. This is the minimum viable Provider abstraction

### Incremental Delivery

1. Setup + Foundational → compilation passes, types defined
2. Add US1 → single Provider call works (MVP!)
3. Add Mock Provider (Phase 7) → CI-friendly, no API key needed
4. Add US2 → multi-provider routing verified
5. Add US3 → Function Calling adapter integrated
6. Add US4 → security hardened
7. Polish → edge cases covered, quickstart validated

### Key Risk: Spring AI API Surface

Spring AI Alibaba 的 API 在快速迭代中，`ChatModel.call()` 的参数签名、`FunctionCallback` 的构造方式可能因版本不同而变化。T024 和 T030 实现时需对当前 Spring AI 版本做 API 确认。

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US-1 完成后没有用户可见入口（无 CLI / Web），需要 US-2（ReAct 循环）完成后才有 Demo
- 现有实现被忽略——所有任务按从零构建设计
- 宪法原则 V（审计 Day One 落库）要求 `llm_calls` 写入贯穿所有 Provider 调用路径，包括 Mock Provider
- Commit after each task or logical group
