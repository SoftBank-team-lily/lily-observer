package com.lily.observer.judge;

import com.lily.observer.ObserverProperties;
import com.lily.observer.metrics.TrafficMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRiskJudgeTest {

    // application.yml 기본값: 최소 20회/분, 1% · 2% · 5%, 응답 2배, 연속 2번
    private final RuleBasedRiskJudge judge = new RuleBasedRiskJudge(
            new ObserverProperties.Judge(20, 0.01, 0.02, 0.05, 2.0, 2),
            Clock.fixed(Instant.parse("2026-10-01T06:00:00Z"), ZoneOffset.UTC));

    private static TrafficMetrics traffic(double rpm, double errorRate, double avgMs, double p95Ms) {
        return new TrafficMetrics(rpm, errorRate, avgMs, p95Ms);
    }

    @Test
    void holdsWhenThereAreTooFewRequests() {
        assertThat(judge.judge("a", traffic(19, 1.0, 10, 10), null).level()).isEqualTo(RiskLevel.HOLD);
    }

    @Test
    void errorRateThresholds() {
        assertThat(judge.judge("a", traffic(100, 0.005, 10, 20), null).level()).isEqualTo(RiskLevel.NORMAL);
        assertThat(judge.judge("a", traffic(100, 0.01, 10, 20), null).level()).isEqualTo(RiskLevel.NOTICE);
        assertThat(judge.judge("a", traffic(100, 0.02, 10, 20), null).level()).isEqualTo(RiskLevel.WARNING);
        assertThat(judge.judge("a", traffic(100, 0.05, 10, 20), null).level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void p95TwiceTheBaselineIsWarningEvenWhenTheAverageLooksFine() {
        Judgment judgment = judge.judge("a", traffic(100, 0, 12, 400), traffic(100, 0, 10, 150));

        assertThat(judgment.level()).isEqualTo(RiskLevel.WARNING);
        assertThat(judgment.reason()).startsWith("p95 응답 400ms");
    }

    @Test
    void fallsBackToAverageWithoutP95AndIgnoresAQuietBaseline() {
        assertThat(judge.judge("a", traffic(100, 0, 40, 0), traffic(100, 0, 10, 0)).reason()).startsWith("평균 응답");
        assertThat(judge.judge("a", traffic(100, 0, 400, 400), traffic(5, 0, 10, 10)).level())
                .isEqualTo(RiskLevel.NORMAL);
    }
}
