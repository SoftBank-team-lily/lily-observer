package com.lily.observer.status;

import io.swagger.v3.oas.annotations.media.Schema;

import com.lily.observer.judge.RiskLevel;
import com.lily.observer.metrics.TrafficMetrics;

import java.time.Instant;

/**
 * 대시보드 패널 상태. 색과 문구를 백엔드에서 정해 화면마다 기준이 달라지지 않게 한다.
 *
 * @param level    HOLD · NORMAL · NOTICE · WARNING · CRITICAL
 * @param score    -1(보류) ~ 3(위험)
 * @param color    gray · green · yellow · red
 * @param action   판정 규칙상 조치 (없음 · 알림 · 주의 알림 · 롤백)
 * @param message  화면에 그대로 보여줄 한 줄
 * @param reason   판정 근거 (예: "5xx 6.2% ≥ 5.0%")
 * @param current  최근 1분
 * @param baseline 비교 기준: 15분 전 ~ 5분 전 (응답 시간 비교용)
 */
public record AppStatus(
        @Schema(example = "lily-test") String app,
        @Schema(example = "default") String namespace,
        @Schema(description = "HOLD · NORMAL · NOTICE · WARNING · CRITICAL", example = "NORMAL") RiskLevel level,
        @Schema(description = "-1(보류) ~ 3(위험)", example = "0") int score,
        @Schema(description = "패널 색: gray · green · yellow · red", example = "green") String color,
        @Schema(description = "판정 규칙상 조치: 판정 보류 · 없음 · 알림 · 주의 알림 · 롤백", example = "없음") String action,
        @Schema(description = "화면에 그대로 보여줄 한 줄", example = "정상이에요") String message,
        @Schema(description = "판정 근거", example = "5xx 0.0%, 응답 18ms") String reason,
        @Schema(description = "최근 1분") TrafficMetrics current,
        @Schema(description = "비교 기준: 15분 전 ~ 5분 전") TrafficMetrics baseline,
        @Schema(description = "판정 시각 (UTC)") Instant judgedAt) {
}
