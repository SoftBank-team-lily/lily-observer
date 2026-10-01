package com.lily.observer.metrics;

/**
 * 한 슬롯(stable / canary / blue / green)의 최근 1분 요청 지표.
 * Actuator 요청(probe)은 빼고 센다.
 *
 * @param requestsPerMinute 분당 요청 수
 * @param errorRate         5xx 비율 (0.0 ~ 1.0). 요청이 없으면 0
 * @param avgLatencyMs      평균 응답 시간. 요청이 없으면 0
 */
public record SlotMetrics(String slot, double requestsPerMinute, double errorRate, double avgLatencyMs) {

    public static SlotMetrics empty(String slot) {
        return new SlotMetrics(slot, 0, 0, 0);
    }
}
