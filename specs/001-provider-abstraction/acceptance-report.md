# 001-provider-abstraction 验收报告

**分支**: dev/first-task  
**PR**: #2 (Draft)  
**提交**: f30bf31c (feat), dfc0c468 (test)  
**日期**: 2026-10-03

---

## 一、门禁证据

### 1. 构建门禁

**oryxos-provider**: 69 tests, 0 failures, 0 errors — **全绿**

```
[INFO] Tests run: 5, Failures: 0, Errors: 0 -- ProviderRegistryValidatorTest
[INFO] Tests run: 6, Failures: 0, Errors: 0 -- ProviderRegistryBootstrapTest
[INFO] Tests run: 3, Failures: 0, Errors: 0 -- ToolSchemaAdapterTest
[INFO] Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**oryxos-core**: 716 tests, 0 failures, 0 errors — **全绿**

**oryxos-storage (SQLite)**: 148 tests, 0 failures, 0 errors — **全绿**

**已知阻塞**: `mvn clean verify` 全量执行时，oryxos-storage 的 9 个 `*PostgresTest` 类因缺少本地 PG 实例报 71 errors。这些是预存测试（非本次引入），需给它们加 `@Tag("integration")` 或条件跳过后才能使 verify 全绿。静态分析（PMD/SpotBugs/FindSecBugs）因 surefire 阶段中断未得到执行。

### 2. 测试覆盖

`ProviderAbstractionIT` — 5 个测试，覆盖 PR Test Plan 全部验收项：

| 测试 | 覆盖点 |
|------|--------|
| `mockProfile_registersOnlyMockProvider` | TP1+TP5: mock 注册、listProviders、hasProvider |
| `callMockProvider_returnsMockResponse` | TP2: call mock 返回含 toolCalls 的响应 |
| `callMockProvider_writesAuditRecord` | TP3: 审计记录写入 llm_calls，字段完整 |
| `callNonexistent_throwsProviderNotFoundException` | TP4: 不存在 provider 抛异常 |
| `fullConfig_requiresApiKey_forNonMockProviders` | TP1 变体: 空 API key 启动报错 |

### 3. 交付物存在性核对

**oryxos-core（新增 8 个文件）**:
- `core/message/Message.java` — 消息数据类 + 4 个静态工厂 ✅
- `core/message/MessageRole.java` — SYSTEM/USER/ASSISTANT/TOOL 枚举 ✅
- `core/message/ToolCall.java` — tool call 值对象 ✅
- `core/provider/ProviderService.java` — 统一接口（扩展 call/listProviders/hasProvider）✅
- `core/provider/LlmResponse.java` — 响应值对象 + hasToolCalls() ✅
- `core/provider/TokenUsage.java` — token 用量值对象 ✅
- `core/provider/ProviderNotFoundException.java` — provider 未找到异常 ✅
- `core/provider/ProviderCallException.java` — provider 调用异常 ✅
- `core/tool/OryxTool.java` — 工具抽象接口 ✅

**oryxos-provider（新增 8 个文件）**:
- `provider/DefaultProviderService.java` — ProviderService 实现 ✅
- `provider/ProviderRegistrar.java` — 显式映射 name→ChatModel ✅
- `provider/FunctionCallingAdapter.java` — OryxTool→Spring AI 格式转换 ✅
- `provider/LlmCallAuditor.java` — 审计写入 ✅
- `provider/ProviderProperties.java` — 配置绑定 ✅
- `provider/JsonSchemaGenerator.java` — 反射生成 JSON Schema ✅
- `provider/ProviderAutoConfiguration.java` — 自动装配入口 ✅
- `AutoConfiguration.imports` — Spring Boot 自动装配注册 ✅

**oryxos-boot（修改/新增 2 个文件）**:
- `application.yml` — deepseek/kimi provider 配置 ✅
- `application-mock.yaml` — mock profile 配置 ✅

**oryxos-storage**: 复用既有 `LlmCall` 实体和 `LlmCallRepository`，`llm_calls` 表建表在 `V1__baseline.sql` ✅

### 4. H4 六条全局不变量

| # | 不变量 | 结果 | 证据 |
|---|--------|------|------|
| ① | Sandbox | **PARTIAL** | ProviderRegistrar 的 OkHttpClient 外部调用缺 TODO 第24节接线注释 |
| ② | LLM 审计 | **PASS** | DefaultProviderService 成功/失败路径均调 auditor.record() |
| ③ | 无明文 key | **PASS** | application.yml 全部 ${ENV_VAR:} 占位 |
| ④ | session_id 封装 | **PASS** | 本节不涉及 session_id |
| ⑤ | 无异步 | **PASS** | 无 Reactor/CompletableFuture/自建线程池 |
| ⑥ | 无自动 tool 执行 | **PASS** | FunctionCallingAdapter.call() 抛异常双保险 |

### 5. 已知缺陷

| ID | 严重度 | 说明 |
|----|--------|------|
| D-001 | CRITICAL | spec/tasks 包名 `com.oryxos` 与代码 `io.oryxos` 不符 |
| D-002 | HIGH | OryxTool/LlmCallAuditor/ProviderNotFoundException 新旧两套重复定义 |
| D-003 | MEDIUM | tasks.md 要求 `LlmCallRecord`，实际复用旧 `LlmCall` 实体 |
| D-004 | MEDIUM | Flyway 迁移 tasks.md 要求 `V1__create_llm_calls.sql`，实际合并在 `V1__baseline.sql` |
| D-005 | LOW | ProviderRegistrar 缺 Sandbox TODO 注释 |

---

## 二、剩余人工项清单

以下项目 harness 无法自动判卷，需人工验证：

1. **真实模型调用**: 配置真实 DeepSeek/Kimi API key，执行 `ProviderService.call("deepseek", null, messages, tools)` 验证返回有效文本响应
2. **多 Provider 路由**: 同时配置 deepseek + kimi，验证不同 provider name 路由到正确 ChatModel
3. **API key 日志审查**: 启动时手动检查控制台日志中无 API key 泄露
4. **PostgreSQL 测试**: 在有 PG 实例的环境中跑全量 `mvn clean verify`
5. **静态分析门禁**: 解决 PG 测试阻塞后，确认 PMD/SpotBugs/FindSecBugs 全绿

**harness 已判卷（5 项自动验收 PASS），以上 5 项等你人工过。**
