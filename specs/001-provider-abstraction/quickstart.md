# Quickstart: Provider 抽象验证指引

**Date**: 2026-10-03 | **Feature**: [spec.md](./spec.md)

## 前置

- JDK 21、Maven；仓库根目录执行；Maven 多模块骨架在位（`mvn compile` 绿）。

## 自动化验收（harness 判卷）

```bash
# 全量门禁（完成定义）：单测 + P3C/SpotBugs/FindSecBugs/PMD
mvn clean verify -pl oryxos-provider,oryxos-core,oryxos-storage -am -Dtest='!*PostgresTest'

# 只跑本节测试类
mvn test -pl oryxos-provider -Dtest='ProviderAbstractionIT,ProviderRegistryValidatorTest,ProviderRegistryBootstrapTest,ToolSchemaAdapterTest'
```

预期：全绿（69 tests, 0 failures）。关键回归：

| 测试 | 守点 |
|---|---|
| `mockProfile_registersOnlyMockProvider` | listProviders 返回 `[mock]`，hasProvider("deepseek") 为 false |
| `callMockProvider_returnsMockResponse` | 返回含 toolCalls 的 Mock 响应，首个 tool name 为 `save_memory` |
| `callMockProvider_writesAuditRecord` | `LlmCallRepository.save()` 被调用，provider="mock"、model="mock-model"、success=true |
| `callNonexistent_throwsProviderNotFoundException` | 抛 `ProviderNotFoundException`，providerName="nonexistent" |
| `fullConfig_requiresApiKey_forNonMockProviders` | 空 API key 的 openai 类型 provider 抛 `IllegalStateException` |

## 回归证据（跨节契约）

```bash
# core 全部测试仍绿（接口扩展未破坏已有代码）
mvn test -pl oryxos-core
# storage SQLite 测试仍绿（审计写入兼容）
mvn test -pl oryxos-storage -Dtest='!*PostgresTest'
# provider 不反向依赖 boot（依赖方向验证）
grep -r "io.oryxos.boot" oryxos-provider/src/main && echo "FAIL" || echo "OK"
# 无明文 key（安全验证）
grep -rn "sk-" oryxos-provider/src/ oryxos-boot/src/main/resources/ && echo "FAIL" || echo "OK"
```

## 人工项（harness 判不了）

1. **真实模型调用**：配置真实 DeepSeek/Kimi API key，调用 `ProviderService.call("deepseek", null, [userMessage("你好")], [])` 验证返回有效文本响应。
2. **多 Provider 路由**：同时配置 deepseek + kimi，验证不同 provider name 路由到正确 ChatModel、互不干扰。
3. **日志无 key 泄露**：启动时人工检查控制台日志中 API key 未出现在任何输出中。
4. **静态分析门禁**：解决 PostgreSQL 测试阻塞后，确认 `mvn clean verify` 含 PMD/SpotBugs/FindSecBugs 全绿。
