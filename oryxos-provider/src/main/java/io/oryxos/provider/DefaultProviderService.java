package io.oryxos.provider;

import io.oryxos.core.message.Message;
import io.oryxos.core.message.ToolCall;
import io.oryxos.core.provider.LlmResponse;
import io.oryxos.core.provider.ProviderCallException;
import io.oryxos.core.provider.ProviderNotFoundException;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.tool.OryxTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class DefaultProviderService implements ProviderService {

  private final ProviderRegistrar registrar;
  private final LlmCallAuditor auditor;

  public DefaultProviderService(ProviderRegistrar registrar, LlmCallAuditor auditor) {
    this.registrar = registrar;
    this.auditor = auditor;
  }

  @Override
  public LlmResponse call(
      String providerName, String model, List<Message> messages, List<OryxTool> tools) {
    ChatModel chatModel = registrar.getChatModel(providerName);
    if (chatModel == null) {
      throw new ProviderNotFoundException(providerName);
    }

    long start = System.nanoTime();
    String actualModel = model != null ? model : registrar.getConfiguredModel(providerName);
    try {
      List<org.springframework.ai.chat.messages.Message> springMessages =
          toSpringMessages(messages);

      Prompt prompt;
      boolean hasTools = tools != null && !tools.isEmpty();
      if (model != null || hasTools) {
        var optionsBuilder = OpenAiChatOptions.builder();
        if (model != null) {
          optionsBuilder.model(model);
        }
        if (hasTools) {
          optionsBuilder.toolCallbacks(FunctionCallingAdapter.toToolCallbacks(tools));
        }
        prompt = new Prompt(springMessages, optionsBuilder.build());
      } else {
        prompt = new Prompt(springMessages);
      }

      ChatResponse response = chatModel.call(prompt);
      long durationMs = (System.nanoTime() - start) / 1_000_000;

      LlmResponse llmResponse = toLlmResponse(response);
      auditor.record(providerName, actualModel, llmResponse.tokenUsage(), durationMs, true, null);
      return llmResponse;

    } catch (ProviderNotFoundException e) {
      throw e;
    } catch (Exception e) {
      long durationMs = (System.nanoTime() - start) / 1_000_000;
      auditor.record(providerName, actualModel, null, durationMs, false, e.getMessage());
      throw new ProviderCallException(providerName, actualModel, e);
    }
  }

  @Override
  public List<String> listProviders() {
    return registrar.listProviderNames();
  }

  @Override
  public boolean hasProvider(String providerName) {
    return registrar.hasProvider(providerName);
  }

  private List<org.springframework.ai.chat.messages.Message> toSpringMessages(
      List<Message> messages) {
    List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
    for (Message msg : messages) {
      result.add(toSpringMessage(msg));
    }
    return result;
  }

  private org.springframework.ai.chat.messages.Message toSpringMessage(Message msg) {
    return switch (msg.role()) {
      case SYSTEM -> new SystemMessage(msg.content());
      case USER -> new UserMessage(msg.content());
      case ASSISTANT -> {
        var builder =
            AssistantMessage.builder().content(msg.content() != null ? msg.content() : "");
        if (msg.toolCalls() != null) {
          builder.toolCalls(
              msg.toolCalls().stream()
                  .map(
                      tc ->
                          new AssistantMessage.ToolCall(
                              tc.id(), "function", tc.name(), tc.arguments()))
                  .toList());
        }
        yield builder.build();
      }
      case TOOL -> {
        var toolResponse =
            new ToolResponseMessage.ToolResponse(msg.toolCallId(), msg.name(), msg.content());
        yield ToolResponseMessage.builder().responses(List.of(toolResponse)).build();
      }
    };
  }

  private LlmResponse toLlmResponse(ChatResponse response) {
    Generation generation = response.getResult();
    if (generation == null) {
      throw new IllegalStateException("LLM returned an empty response");
    }
    AssistantMessage output = generation.getOutput();

    String content = output.getText();
    String finishReason =
        generation.getMetadata() != null ? generation.getMetadata().getFinishReason() : null;

    List<ToolCall> toolCalls = null;
    if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
      toolCalls =
          output.getToolCalls().stream()
              .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
              .toList();
    }

    var usage = response.getMetadata().getUsage();
    TokenUsage tokenUsage =
        new TokenUsage(
            usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

    return new LlmResponse(content, toolCalls, tokenUsage, finishReason);
  }
}
