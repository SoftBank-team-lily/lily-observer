package com.lily.observer.metrics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 앱의 일정 구간 요청 지표. ingress-nginx 입구에서 센 값이라 앱 언어와 상관없다.
 *
 * @param requestsPerMinute 분당 요청 수
 * @param errorRate         5xx 비율 (0.0 ~ 1.0). 요청이 없으면 0
 * @param avgLatencyMs      평균 응답 시간. 요청이 없으면 0
 * @param p95LatencyMs      95% 요청이 이 시간 안에 끝남. 느린 요청을 평균보다 잘 드러낸다. 요청이 없으면 0
 */
public record TrafficMetrics(
        @Schema(description = "분당 요청 수 (응답 코드 무관)", example = "42.0") double requestsPerMinute,
        @Schema(description = "5xx 비율 0.0 ~ 1.0. 4xx 는 에러로 세지 않음", example = "0.024") double errorRate,
        @Schema(description = "평균 응답 시간 (ms)", example = "18.0") double avgLatencyMs,
        @Schema(description = "95% 요청이 이 시간 안에 끝남 (ms)", example = "45.0") double p95LatencyMs) {

    public static TrafficMetrics empty() {
        return new TrafficMetrics(0, 0, 0, 0);
    }
}
