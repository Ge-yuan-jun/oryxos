package io.oryxos.core.provider;

import io.oryxos.core.message.Message;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import java.util.List;

public interface ProviderService {

  default LlmResponse call(
      String providerName, String model, List<Message> messages, List<OryxTool> tools) {
    throw new UnsupportedOperationException("call() not implemented");
  }

  default List<String> listProviders() {
    throw new UnsupportedOperationException("listProviders() not implemented");
  }

  default boolean hasProvider(String providerName) {
    throw new UnsupportedOperationException("hasProvider() not implemented");
  }

  default ProviderResponse chat(String sessionId, Profile profile, ProviderRequest request) {
    throw new UnsupportedOperationException("Legacy chat() removed; use call()");
  }

  default ProviderResponse chatStream(
      String sessionId,
      Profile profile,
      ProviderRequest request,
      java.util.function.Consumer<String> onToken) {
    throw new UnsupportedOperationException("Legacy chatStream() removed; use call()");
  }
}
