# Implementation Plan: Provider 抽象——对接大模型的统一入口

**Branch**: `dev/first-task` | **Date**: 2026-10-02 | **Spec**: `specs/001-provider-abstraction/spec.md`

**Input**: Feature specification from `/specs/001-provider-abstraction/spec.md`

## Summary

构建 OryxOS 的 LLM 调用基础层：一个统一的 `ProviderService` 抽象，通过显式的 provider name → `ChatModel` 映射，让上层（未来的 ReAct 循环）不感知具体调的是哪家 LLM。Spring AI Alibaba 吸收各家 LLM 的协议差异，OryxOS 只做薄包装。同时搭建 Maven 多模块骨架、审计表（`llm_calls`）Day One 写入、Mock Provider 内置、Function Calling 格式适配（仅 Schema 生成，禁用自动执行）。

## Technical Context

**Language/Version**: Java 21（虚拟线程）

**Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba（含 OpenAI-compatible / DashScope connectors）, Maven 多模块

**Storage**: SQLite（默认）+ Spring Data JPA + Flyway 迁移管理（`llm_calls` 审计表从 Day One 写入）

**Testing**: JUnit 5 + Spring Boot Test + MockProvider（内置 Mock Provider 替代外部 LLM 依赖）

**Target Platform**: JVM（单 fat JAR，Linux / macOS / Windows）

**Project Type**: Maven 多模块库（US-1 涉及 `oryxos-core`、`oryxos-provider`、`oryxos-storage`、`oryxos-boot` 四个模块）

**Performance Goals**: 首次 LLM 调用 < 5s 响应（排除网络延迟）；同步 + 虚拟线程模型

**Constraints**:
- 同步阻塞执行，MUST NOT 引入 Reactor / WebFlux / CompletableFuture
- Provider 显式映射，MUST NOT 扫描 Spring 容器 ChatModel Bean 做路由
- Spring AI 仅用于协议转换 + JSON Schema 生成，MUST 禁用自动 tool 执行和 eager 自动装配
- API key 走环境变量 / `${ENV_VAR}` 占位，MUST NOT 明文写入日志或代码
- 审计表 `llm_calls` Day One 写入，不可省

**Scale/Scope**: 核心阶段支持同时配置 3+ 个 LLM Provider，互不干扰

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 级别 | 合规状态 | 说明 |
|---|------|------|----------|------|
| I | 自实现 ReAct 循环 | NON-NEG | ✅ PASS | US-1 不涉及 ReAct 循环实现；`ProviderService.call()` 返回原始 LLM 响应，由未来的 ReAct 循环消费，不引入 Spring AI Agent 抽象 |
| II | Spring AI 仅做协议转换与 Schema 生成 | NON-NEG | ✅ PASS | 明确禁用 Spring AI 自动 tool 执行（`ChatModel.call(Prompt)` 直接调用）；禁用 eager 自动装配（如 `DashScopeAutoConfiguration`） |
| III | Provider 显式映射 | — | ✅ PASS | `ProviderService` 维护 `Map<String, ChatModel>` 显式映射表，由 `application.yaml` 配置驱动构建，不依赖 Bean 类型扫描 |
| IV | 目录 = Agent，Skill 软连接 | — | N/A | US-1 不涉及 Agent 目录或 Skill 绑定 |
| V | 审计 Day One 落库 | NON-NEG | ✅ PASS | `llm_calls` 表在 US-1 就创建并写入；每次 LLM 调用记录 provider name、model、token usage、timestamp |
| VI | 安全地基 | NON-NEG | ✅ PASS | API key 通过 `${ENV_VAR}` 占位符加载，不出现在日志/代码/提交历史；配置缺失时启动报错 |
| VII | 同步 + 虚拟线程 | — | ✅ PASS | 全程同步阻塞调用，不引入异步编程模型 |
| VIII | 目录配置即 Agent | — | N/A | US-1 不涉及 Agent 实例化；只提供 Provider 能力层 |

**Gate 结论**: 全部通过，无违规项。进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/001-provider-abstraction/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── provider-service-contract.md
└── tasks.md             # Phase 2 output (NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
oryxos-core/
└── src/main/java/com/oryxos/core/
    ├── provider/
    │   ├── ProviderService.java          # 统一 LLM 调用门面接口
    │   └── LlmResponse.java             # LLM 响应包装（含 tool calls + token usage）
    ├── profile/
    │   └── Profile.java                  # Agent 运行配置（含 provider name 引用）
    ├── message/
    │   ├── Message.java                  # 对话消息抽象
    │   └── MessageRole.java             # 消息角色枚举
    └── tool/
        └── OryxTool.java                # 工具抽象接口

oryxos-provider/
└── src/main/java/com/oryxos/provider/
    ├── DefaultProviderService.java       # ProviderService 实现（显式映射）
    ├── ProviderProperties.java           # 配置属性绑定
    ├── ProviderRegistrar.java            # 启动时构建映射表
    ├── MockChatModel.java                # Mock Provider 实现
    ├── FunctionCallingAdapter.java       # OryxTool → Spring AI tools 格式转换
    └── LlmCallAuditor.java              # 审计记录写入

oryxos-storage/
└── src/main/java/com/oryxos/storage/
    ├── entity/
    │   └── LlmCallRecord.java           # llm_calls 审计实体
    ├── repository/
    │   └── LlmCallRepository.java       # JPA Repository
    └── db/migration/
        └── sqlite/
            └── V1__create_llm_calls.sql  # Flyway 建表迁移

oryxos-boot/
└── src/main/resources/
    ├── application.yaml                  # Provider 配置模板
    └── application-mock.yaml             # Mock Profile（CI 用）
```

**Structure Decision**: 遵循技术方案 §10 的 Maven 多模块划分。`oryxos-core` 放接口和抽象，`oryxos-provider` 放实现（依赖倒置），`oryxos-storage` 放持久化，`oryxos-boot` 做装配和配置。US-1 聚焦这四个模块，其余模块（如 `oryxos-tool`、`oryxos-memory`）在后续 US 中填充。

## Complexity Tracking

> 无违规项，无需论证。
