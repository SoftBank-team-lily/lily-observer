package com.lily.observer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** application.yml 의 observer.* */
@ConfigurationProperties(prefix = "observer")
public record ObserverProperties(
        String apiToken,
        Prometheus prometheus,
        Watch watch,
        Judge judge,
        Rollback rollback,
        Logs logs,
        Store store
) {

    public record Prometheus(String url, String requestMetric) {}

    public record Watch(Duration interval, Duration window) {}

    public record Judge(
            int minRequests,
            double noticeErrorRate,
            double warningErrorRate,
            double criticalErrorRate,
            double latencyRatio,
            int consecutive) {}

    /**
     * 롤백은 lily-cicd 의 POST /api/deployments/{app}/rollback 을 부른다.
     *
     * @param enabled  false 면 호출하지 않고 "롤백했을 것"만 기록한다
     * @param cicdUrl  lily-cicd 주소 (클러스터 안: http://lily-cicd.lily-system.svc)
     */
    public record Rollback(boolean enabled, String cicdUrl) {}

    public record Logs(boolean cloudwatchEnabled, String logGroupPrefix, String region) {}

    public record Store(String type, Dynamodb dynamodb) {}

    public record Dynamodb(String table, String endpoint, String region, boolean createTable) {}
}
