package com.lily.observer.judge;

import com.lily.observer.metrics.TrafficMetrics;

import java.time.Instant;

/**
 * 판정 한 번의 결과.
 *
 * @param target   새 버전 배포 후 지표
 * @param baseline 비교 기준 지표 (배포 전 같은 앱). 없으면 null
 */
public record Judgment(
        String app,
        RiskLevel level,
        String reason,
        TrafficMetrics target,
        TrafficMetrics baseline,
        Instant judgedAt) {
}
