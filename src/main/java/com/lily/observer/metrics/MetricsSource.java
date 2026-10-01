package com.lily.observer.metrics;

import java.time.Duration;
import java.time.Instant;

/**
 * 앱별 요청 지표를 읽는다.
 * 기본 구현은 Prometheus(ingress-nginx 지표). 테스트나 다른 수집 방식은 같은 타입의 빈으로 바꾼다.
 */
public interface MetricsSource {

    /**
     * @param at     구간의 끝 시각. 배포 전과 비교하려면 배포 시각을 넣는다
     * @param window 구간 길이 (예: 최근 1분)
     */
    TrafficMetrics traffic(String namespace, String app, Instant at, Duration window);
}
