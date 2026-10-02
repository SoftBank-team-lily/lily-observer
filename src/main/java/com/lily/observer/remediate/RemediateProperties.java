package com.lily.observer.remediate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 기본은 꺼져 있다. 켜도 frontend 주소와 토큰이 없으면 보내지 않는다. */
@ConfigurationProperties(prefix = "observer.remediate")
public record RemediateProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String frontendUrl,
        @DefaultValue("") String token) {
}
