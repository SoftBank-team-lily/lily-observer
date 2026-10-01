package com.lily.observer.metrics;

/** 지표 저장소에 닿지 못했을 때. 판정 루프는 이번 회차만 건너뛴다 */
public class MetricsUnavailableException extends RuntimeException {

    public MetricsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
