package io.oryxos.provider;

import io.oryxos.core.provider.TokenUsage;
import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import org.springframework.stereotype.Component;

@Component
public class LlmCallAuditor {

  private final LlmCallRepository repository;

  public LlmCallAuditor(LlmCallRepository repository) {
    this.repository = repository;
  }

  public void record(
      String providerName,
      String model,
      TokenUsage usage,
      long durationMs,
      boolean success,
      String errorMessage) {
    var call = new LlmCall();
    call.setSessionId("provider-direct");
    call.setProvider(providerName);
    call.setModel(model);
    if (usage != null) {
      call.setPromptTokens((int) usage.inputTokens());
      call.setCompletionTokens((int) usage.outputTokens());
      call.setTotalTokens((int) usage.totalTokens());
    }
    call.setDurationMs(durationMs);
    call.setSuccess(success);
    call.setErrorMessage(errorMessage);
    repository.save(call);
  }
}
