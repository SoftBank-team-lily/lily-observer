package com.lily.observer.status;

import com.lily.observer.ObserverProperties;
import com.lily.observer.judge.RiskLevel;
import com.lily.observer.judge.RuleBasedRiskJudge;
import com.lily.observer.metrics.MetricsSource;
import com.lily.observer.metrics.TrafficMetrics;
import com.lily.observer.metrics.TrafficPoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppStatusServiceTest {

    private static AppStatus statusFor(TrafficMetrics current) {
        MetricsSource metrics = new MetricsSource() {
            @Override
            public TrafficMetrics traffic(String namespace, String app, Instant at, Duration window) {
                return window.equals(Duration.ofMinutes(1)) ? current : new TrafficMetrics(100, 0, 10, 20);
            }

            @Override
            public List<TrafficPoint> series(String namespace, String app, Instant from, Instant to, Duration step) {
                return List.of();
            }
        };
        ObserverProperties properties = new ObserverProperties(null, null, null,
                new ObserverProperties.Judge(20, 0.01, 0.02, 0.05, 2.0, 2), null, null, null);
        return new AppStatusService(metrics, new RuleBasedRiskJudge(properties)).status("default", "lily-test");
    }

    @Test
    void criticalIsRedWithARollbackMessage() {
        AppStatus status = statusFor(new TrafficMetrics(60, 0.062, 10, 20));

        assertThat(status.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(status.color()).isEqualTo("red");
        assertThat(status.score()).isEqualTo(3);
        assertThat(status.message()).isEqualTo("위험: 에러율 6.2%. 이전 버전으로 되돌리는 것을 권장해요");
        assertThat(status.baseline().requestsPerMinute()).isEqualTo(100);
    }

    @Test
    void quietAppIsGray() {
        AppStatus status = statusFor(new TrafficMetrics(3, 0, 10, 20));

        assertThat(status.color()).isEqualTo("gray");
        assertThat(status.message()).isEqualTo("요청이 적어(분당 3회) 판단을 보류해요");
    }

    @Test
    void healthyAppIsGreen() {
        AppStatus status = statusFor(new TrafficMetrics(60, 0, 10, 20));

        assertThat(status.color()).isEqualTo("green");
        assertThat(status.message()).isEqualTo("정상이에요");
    }
}
