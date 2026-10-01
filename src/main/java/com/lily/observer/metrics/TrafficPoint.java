package com.lily.observer.metrics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 추이 그래프의 한 점. 각 값은 그 시각 기준 최근 1분 값이다.
 *
 * @param requestsPerMinute 분당 요청 수
 * @param errorRate         5xx 비율 (0.0 ~ 1.0)
 * @param avgLatencyMs      평균 응답 시간
 * @param p95LatencyMs      95 퍼센타일 응답 시간
 */
public record TrafficPoint(
        @Schema(description = "기준 시각 (UTC). 값은 이 시각 기준 최근 1분", example = "2026-10-01T06:22:03Z") Instant at,
        @Schema(description = "분당 요청 수", example = "4.0") double requestsPerMinute,
        @Schema(description = "5xx 비율 0.0 ~ 1.0", example = "1.0") double errorRate,
        @Schema(description = "평균 응답 시간 (ms)", example = "3.3") double avgLatencyMs,
        @Schema(description = "p95 응답 시간 (ms)", example = "9.5") double p95LatencyMs) {
}
