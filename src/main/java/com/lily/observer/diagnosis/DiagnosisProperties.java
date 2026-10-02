package com.lily.observer.diagnosis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "observer.diagnosis")
public record DiagnosisProperties(@DefaultValue("false") boolean enabled,
                                  @DefaultValue("http://lily-builder.lily-system.svc") String builderUrl,
                                  @DefaultValue("") String apiToken) {}
