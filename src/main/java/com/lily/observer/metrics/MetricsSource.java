package com.lily.observer.metrics;

/**
 * 슬롯별 요청 지표를 읽는다.
 * 기본 구현은 Prometheus. 테스트나 다른 수집 방식은 같은 타입의 빈으로 바꾼다.
 */
public interface MetricsSource {

    SlotMetrics slot(String namespace, String app, String slot);
}
