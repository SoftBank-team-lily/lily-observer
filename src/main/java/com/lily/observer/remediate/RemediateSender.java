package com.lily.observer.remediate;

import com.lily.observer.logs.LogEntry;
import com.lily.observer.logs.LogSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 최근 15분 로그로 사건을 만들고, 스위치가 켜져 있으면 frontend 로 보낸다. 롤백은 부르지 않는다. */
@Component
public class RemediateSender {

    private static final Duration SINCE = Duration.ofMinutes(15);

    private final RemediateProperties properties;
    private final LogSource logs;
    private final Clock clock = Clock.systemUTC();
    private final RestClient http = RestClient.create();

    public RemediateSender(RemediateProperties properties, LogSource logs) {
        this.properties = properties;
        this.logs = logs;
    }

    public Map<String, Object> send(String namespace, String app) {
        if (!properties.enabled()) {
            return Remediate.consider(false, app, List.of());
        }
        List<LogEntry> recent = logs.recent(namespace, app, clock.instant().minus(SINCE), false, 200);
        Map<String, Object> outcome = Remediate.consider(true, app, recent);
        if (!"ready".equals(outcome.get("status"))) {
            return outcome;
        }
        if (properties.frontendUrl().isBlank() || properties.token().isBlank()) {
            return Map.of("status", "rejected", "reason", "frontend 주소나 토큰이 없다");
        }
        http.post()
                .uri(properties.frontendUrl().replaceAll("/$", "") + "/api/internal/remediate")
                .header("Authorization", "Bearer " + properties.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(outcome.get("incident"))
                .retrieve()
                .toBodilessEntity();
        return Map.of("status", "sent");
    }
}
