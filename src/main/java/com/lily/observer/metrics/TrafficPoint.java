package com.lily.observer.metrics;

import java.time.Instant;

/**
 * 추이 그래프의 한 점. 각 값은 그 시각 기준 최근 1분 값이다.
 *
 * @param requestsPerMinute 분당 요청 수
 * @param errorRate         5xx 비율 (0.0 ~ 1.0)
 * @param avgLatencyMs      평균 응답 시간
 */
public record TrafficPoint(Instant at, double requestsPerMinute, double errorRate, double avgLatencyMs) {
}
