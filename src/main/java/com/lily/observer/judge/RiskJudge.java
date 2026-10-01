package com.lily.observer.judge;

import com.lily.observer.metrics.TrafficMetrics;

/**
 * 새 버전 지표로 위험도를 정한다. 규칙을 바꾸려면 같은 타입의 빈을 등록한다.
 *
 * @param baseline 비교할 배포 전 지표. 비교 대상이 없으면 null
 */
public interface RiskJudge {

    Judgment judge(String app, TrafficMetrics target, TrafficMetrics baseline);
}
