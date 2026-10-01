package com.lily.observer.metrics;

/**
 * 한 앱의 일정 구간 요청 지표. ingress-nginx 입구에서 센 값이라 앱 언어와 상관없다.
 *
 * @param requestsPerMinute 분당 요청 수
 * @param errorRate         5xx 비율 (0.0 ~ 1.0). 요청이 없으면 0
 * @param avgLatencyMs      평균 응답 시간. 요청이 없으면 0
 */
public record TrafficMetrics(double requestsPerMinute, double errorRate, double avgLatencyMs) {

    public static TrafficMetrics empty() {
        return new TrafficMetrics(0, 0, 0);
    }
}
