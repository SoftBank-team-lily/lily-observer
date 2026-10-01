package com.lily.observer.judge;

import com.lily.observer.metrics.SlotMetrics;

import java.time.Instant;

/**
 * 판정 한 번의 결과.
 *
 * @param target   새 버전 슬롯 지표
 * @param baseline 비교 기준 슬롯 지표 (canary 의 stable). 없으면 null
 */
public record Judgment(
        String app,
        RiskLevel level,
        String reason,
        SlotMetrics target,
        SlotMetrics baseline,
        Instant judgedAt) {
}
