package com.lily.observer.judge;

import com.lily.observer.ObserverProperties;
import com.lily.observer.metrics.TrafficMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 기본 규칙. 에러율이 가장 강한 신호이고, 응답 시간은 기준(배포 전) 지표가 있을 때만 본다.
 *
 * <pre>
 * 요청 &lt; min-requests                         → HOLD
 * 에러율 ≥ critical                            → CRITICAL (롤백)
 * 에러율 ≥ warning  또는 p95 응답 시간 ≥ 기준 x ratio → WARNING
 * 에러율 ≥ notice                              → NOTICE
 * 그 외                                        → NORMAL
 * </pre>
 */
@Component
public class RuleBasedRiskJudge implements RiskJudge {

    private final ObserverProperties.Judge rules;
    private final Clock clock;

    @Autowired
    public RuleBasedRiskJudge(ObserverProperties properties) {
        this(properties.judge(), Clock.systemUTC());
    }

    RuleBasedRiskJudge(ObserverProperties.Judge rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public Judgment judge(String app, TrafficMetrics target, TrafficMetrics baseline) {
        Instant now = clock.instant();
        if (target.requestsPerMinute() < rules.minRequests()) {
            return new Judgment(app, RiskLevel.HOLD,
                    String.format("요청 %.0f/분 < %d, 판정 보류", target.requestsPerMinute(), rules.minRequests()),
                    target, baseline, now);
        }
        double errorRate = target.errorRate();
        if (errorRate >= rules.criticalErrorRate()) {
            return new Judgment(app, RiskLevel.CRITICAL,
                    String.format("5xx %.1f%% ≥ %.1f%%", errorRate * 100, rules.criticalErrorRate() * 100),
                    target, baseline, now);
        }
        if (errorRate >= rules.warningErrorRate()) {
            return new Judgment(app, RiskLevel.WARNING,
                    String.format("5xx %.1f%% ≥ %.1f%%", errorRate * 100, rules.warningErrorRate() * 100),
                    target, baseline, now);
        }
        if (slowerThanBaseline(target, baseline)) {
            boolean p95 = useP95(target, baseline);
            return new Judgment(app, RiskLevel.WARNING,
                    String.format("%s 응답 %.0fms ≥ 기준 %.0fms x %.1f", p95 ? "p95" : "평균",
                            latency(target, p95), latency(baseline, p95), rules.latencyRatio()),
                    target, baseline, now);
        }
        if (errorRate >= rules.noticeErrorRate()) {
            return new Judgment(app, RiskLevel.NOTICE,
                    String.format("5xx %.1f%% ≥ %.1f%%", errorRate * 100, rules.noticeErrorRate() * 100),
                    target, baseline, now);
        }
        return new Judgment(app, RiskLevel.NORMAL,
                String.format("5xx %.1f%%, 응답 %.0fms", errorRate * 100, target.avgLatencyMs()),
                target, baseline, now);
    }

    private boolean slowerThanBaseline(TrafficMetrics target, TrafficMetrics baseline) {
        if (baseline == null || baseline.requestsPerMinute() < rules.minRequests()) {
            return false;
        }
        boolean p95 = useP95(target, baseline);
        double base = latency(baseline, p95);
        return base > 0 && latency(target, p95) >= base * rules.latencyRatio();
    }

    /** 느린 요청은 평균에 묻히므로 p95 를 먼저 본다. 둘 중 하나라도 p95 가 없으면 평균 */
    private static boolean useP95(TrafficMetrics target, TrafficMetrics baseline) {
        return target.p95LatencyMs() > 0 && baseline.p95LatencyMs() > 0;
    }

    private static double latency(TrafficMetrics metrics, boolean p95) {
        return p95 ? metrics.p95LatencyMs() : metrics.avgLatencyMs();
    }
}
