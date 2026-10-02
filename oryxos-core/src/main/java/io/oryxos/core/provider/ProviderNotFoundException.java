package io.oryxos.core.provider;

public class ProviderNotFoundException extends RuntimeException {
  private final String providerName;

  public ProviderNotFoundException(String providerName) {
    super("Provider not found: " + providerName);
    this.providerName = providerName;
  }

  public String getProviderName() {
    return providerName;
  }
}
