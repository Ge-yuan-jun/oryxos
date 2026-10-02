package io.oryxos.provider;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oryxos")
public class ProviderProperties {

  private List<ProviderEntry> providers = new ArrayList<>();

  public List<ProviderEntry> getProviders() {
    return List.copyOf(providers);
  }

  public void setProviders(List<ProviderEntry> providers) {
    this.providers = new ArrayList<>(providers);
  }

  public static class ProviderEntry {
    private String name;
    private String type;
    private String model;
    private String apiKey;
    private String baseUrl;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    @Override
    public String toString() {
      return "ProviderEntry{name='"
          + name
          + "', type='"
          + type
          + "', model='"
          + model
          + "', baseUrl='"
          + baseUrl
          + "'}";
    }
  }
}
