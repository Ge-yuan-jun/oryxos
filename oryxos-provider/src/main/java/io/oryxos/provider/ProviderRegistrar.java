package io.oryxos.provider;

import com.openai.core.Timeout;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistrar {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistrar.class);
  private static final Set<String> KNOWN_TYPES = Set.of("openai", "dashscope", "mock");

  private static final String SLASH = "/";

  /** 连接超时（秒）：默认 10，{@code -Doryxos.llm.connect-timeout-seconds=N} 覆盖。 */
  static final String CONNECT_TIMEOUT_PROP = "oryxos.llm.connect-timeout-seconds";

  /** 读取超时（秒）：默认 120（推理模型长回答留足余量），{@code -Doryxos.llm.read-timeout-seconds=N} 覆盖。 */
  static final String READ_TIMEOUT_PROP = "oryxos.llm.read-timeout-seconds";

  private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
  private static final long DEFAULT_READ_TIMEOUT_SECONDS = 120;

  /** 末尾版本段（如 /v1）：填了 /vN 保留，否则补 /v1。 */
  private static final Pattern TRAILING_VERSION = Pattern.compile(".*/v\\d+$");

  private final ProviderProperties properties;
  private final Map<String, ChatModel> providers = new LinkedHashMap<>();
  private final Map<String, String> configuredModels = new LinkedHashMap<>();

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ProviderProperties is a shared Spring bean dependency and is intentionally retained; "
              + "copying or wrapping it would break bean semantics.")
  public ProviderRegistrar(ProviderProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "CRLF_INJECTION_LOGS",
      justification = "日志中的 provider 名已经 sanitize() 消去 CR/LF；taint 分析不跨方法追踪该消毒，故局部抑制")
  void init() {
    List<ProviderProperties.ProviderEntry> entries = properties.getProviders();
    if (entries == null || entries.isEmpty()) {
      log.warn("No providers configured under oryxos.providers");
      return;
    }
    Map<String, Integer> nameCount = new LinkedHashMap<>();
    for (var entry : entries) {
      nameCount.merge(entry.getName(), 1, Integer::sum);
    }
    List<String> duplicates =
        nameCount.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
    if (!duplicates.isEmpty()) {
      throw new IllegalStateException("Duplicate provider names: " + duplicates);
    }

    for (var entry : entries) {
      if (!KNOWN_TYPES.contains(entry.getType())) {
        throw new IllegalStateException(
            "Unknown provider type '"
                + entry.getType()
                + "' for provider '"
                + entry.getName()
                + "'. Supported types: "
                + KNOWN_TYPES);
      }
      if (!"mock".equals(entry.getType())) {
        if (entry.getApiKey() == null || entry.getApiKey().isBlank()) {
          throw new IllegalStateException(
              "API key is required for provider '"
                  + entry.getName()
                  + "'. Set via environment variable.");
        }
      }
      ChatModel chatModel = createChatModel(entry);
      providers.put(entry.getName(), chatModel);
      configuredModels.put(entry.getName(), entry.getModel());
    }
    log.info(
        "Registered providers: {}",
        providers.keySet().stream().map(ProviderRegistrar::sanitize).toList());
  }

  /** 日志消毒：去掉 provider 名中的 CR/LF，防止日志伪造（CRLF 注入）。 */
  private static String sanitize(String value) {
    return value == null ? "<unknown>" : value.replace('\r', '_').replace('\n', '_');
  }

  private ChatModel createChatModel(ProviderProperties.ProviderEntry entry) {
    return switch (entry.getType()) {
      case "openai", "dashscope" -> {
        Duration connectTimeout =
            Duration.ofSeconds(Long.getLong(CONNECT_TIMEOUT_PROP, DEFAULT_CONNECT_TIMEOUT_SECONDS));
        Duration readTimeout =
            Duration.ofSeconds(Long.getLong(READ_TIMEOUT_PROP, DEFAULT_READ_TIMEOUT_SECONDS));
        var options =
            OpenAiChatOptions.builder()
                .model(entry.getModel())
                .baseUrl(normalizeBaseUrl(entry.getBaseUrl()))
                .apiKey(entry.getApiKey())
                .timeout(readTimeout)
                .maxRetries(0)
                .build();
        yield OpenAiChatModel.builder()
            .options(options)
            .httpClientBuilderCustomizer(
                builder -> builder.timeout(okHttpTimeout(connectTimeout, readTimeout)))
            .build();
      }
      case "mock" -> new MockChatModel();
      default -> throw new IllegalStateException("Unsupported type: " + entry.getType());
    };
  }

  private static Timeout okHttpTimeout(Duration connectTimeout, Duration readTimeout) {
    return Timeout.builder()
        .connect(connectTimeout)
        .read(readTimeout)
        .write(readTimeout)
        .request(readTimeout)
        .build();
  }

  /**
   * OpenAI Java SDK 期望 baseUrl 含版本段（默认 {@code https://api.openai.com/v1}）。填了 /vN 则保留；去掉末尾斜杠，否则补
   * {@code /v1}。
   */
  static String normalizeBaseUrl(String baseUrl) {
    String url = baseUrl == null ? "" : baseUrl.strip();
    while (url.endsWith(SLASH)) {
      url = url.substring(0, url.length() - SLASH.length());
    }
    if (url.isEmpty() || TRAILING_VERSION.matcher(url).matches()) {
      return url;
    }
    return url + "/v1";
  }

  public ChatModel getChatModel(String providerName) {
    return providers.get(providerName);
  }

  public String getConfiguredModel(String providerName) {
    return configuredModels.get(providerName);
  }

  public List<String> listProviderNames() {
    return new ArrayList<>(providers.keySet());
  }

  public boolean hasProvider(String providerName) {
    return providers.containsKey(providerName);
  }
}
