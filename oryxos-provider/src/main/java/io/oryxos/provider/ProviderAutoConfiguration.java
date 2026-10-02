package io.oryxos.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
@ComponentScan(basePackageClasses = ProviderAutoConfiguration.class)
public class ProviderAutoConfiguration {}
