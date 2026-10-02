package io.oryxos.core.provider;

public class ProviderCallException extends RuntimeException {
  private final String providerName;
  private final String model;

  public ProviderCallException(String providerName, String model, Throwable cause) {
    super("Provider call failed: " + providerName + " / " + model, cause);
    this.providerName = providerName;
    this.model = model;
  }

  public String getProviderName() {
    return providerName;
  }

  public String getModel() {
    return model;
  }
}
