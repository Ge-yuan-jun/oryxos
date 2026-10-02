package io.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.message.Message;
import io.oryxos.core.provider.LlmResponse;
import io.oryxos.core.provider.ProviderNotFoundException;
import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Provider Abstraction 层集成测试——覆盖 PR #2 Test Plan 的 5 条验收项。
 *
 * <p>TP1（变体）+ TP5：mock profile 仅注册 mock provider<br>
 * TP2：call("mock", ...) 返回 Mock 响应<br>
 * TP3：call 后审计记录写入 llm_calls（provider_name/model/token/duration_ms）<br>
 * TP4：call("nonexistent", ...) 抛 ProviderNotFoundException
 */
class ProviderAbstractionIT {

  private DefaultProviderService service;
  private ProviderRegistrar registrar;
  private LlmCallRepository repository;

  @BeforeEach
  void setUp() {
    var entry = new ProviderProperties.ProviderEntry();
    entry.setName("mock");
    entry.setType("mock");
    entry.setModel("mock-model");

    var properties = new ProviderProperties();
    properties.setProviders(List.of(entry));

    registrar = new ProviderRegistrar(properties);
    registrar.init();

    repository = mock(LlmCallRepository.class);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var auditor = new LlmCallAuditor(repository);
    service = new DefaultProviderService(registrar, auditor);
  }

  @Test
  @DisplayName("TP1+TP5: mock profile 仅注册 mock provider，listProviders 返回 [mock]")
  void mockProfile_registersOnlyMockProvider() {
    List<String> providers = service.listProviders();
    assertEquals(List.of("mock"), providers);
    assertTrue(service.hasProvider("mock"));
    assertFalse(service.hasProvider("deepseek"));
    assertFalse(service.hasProvider("kimi"));
  }

  @Test
  @DisplayName("TP2: call mock provider 返回含 toolCalls 的 Mock 响应")
  void callMockProvider_returnsMockResponse() {
    LlmResponse response =
        service.call("mock", null, List.of(Message.userMessage("hello")), List.of());

    assertNotNull(response);
    assertNotNull(response.toolCalls(), "MockChatModel 首轮应返回 tool call");
    assertFalse(response.toolCalls().isEmpty());
    assertEquals("save_memory", response.toolCalls().get(0).name());
    assertNotNull(response.tokenUsage());
  }

  @Test
  @DisplayName("TP3: call 后审计记录写入 llm_calls，字段完整")
  void callMockProvider_writesAuditRecord() {
    service.call("mock", null, List.of(Message.userMessage("hello")), List.of());

    ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
    verify(repository).save(captor.capture());

    LlmCall audit = captor.getValue();
    assertEquals("mock", audit.getProvider());
    assertEquals("mock-model", audit.getModel());
    assertTrue(audit.isSuccess());
    assertTrue(audit.getDurationMs() >= 0);
    assertNotNull(audit.getSessionId());
  }

  @Test
  @DisplayName("TP4: call 不存在的 provider 抛 ProviderNotFoundException")
  void callNonexistent_throwsProviderNotFoundException() {
    ProviderNotFoundException ex =
        assertThrows(
            ProviderNotFoundException.class,
            () ->
                service.call(
                    "nonexistent", null, List.of(Message.userMessage("hello")), List.of()));
    assertEquals("nonexistent", ex.getProviderName());
  }

  @Test
  @DisplayName("TP1 完整变体: 三 provider 配置需有效 API key 才能注册")
  void fullConfig_requiresApiKey_forNonMockProviders() {
    var deepseek = new ProviderProperties.ProviderEntry();
    deepseek.setName("deepseek");
    deepseek.setType("openai");
    deepseek.setModel("deepseek-chat");
    deepseek.setApiKey("");
    deepseek.setBaseUrl("https://api.deepseek.com");

    var mockEntry = new ProviderProperties.ProviderEntry();
    mockEntry.setName("mock");
    mockEntry.setType("mock");
    mockEntry.setModel("mock-model");

    var properties = new ProviderProperties();
    properties.setProviders(List.of(deepseek, mockEntry));

    var reg = new ProviderRegistrar(properties);
    assertThrows(IllegalStateException.class, reg::init, "空 API key 应启动报错");
  }
}
