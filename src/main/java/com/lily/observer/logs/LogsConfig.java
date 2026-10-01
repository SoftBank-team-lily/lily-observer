package com.lily.observer.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lily.observer.ObserverProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * observer.logs.cloudwatch-enabled 가 false 면 AWS 에 붙지 않는다 (로컬 실행용). 이때 로그 조회는 503.
 */
@Configuration
public class LogsConfig {

    @Bean
    LogSource logSource(ObserverProperties properties, ObjectMapper json) {
        if (!properties.logs().cloudwatchEnabled()) {
            return (namespace, app, since, errorsOnly, limit) -> {
                throw new LogsUnavailableException("로그 조회가 꺼져 있습니다 (CLOUDWATCH_LOGS_ENABLED=false)");
            };
        }
        return new CloudWatchLogSource(properties, json);
    }
}
