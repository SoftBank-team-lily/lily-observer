package com.lily.observer.status;

import com.lily.observer.judge.Judgment;
import com.lily.observer.judge.RiskJudge;
import com.lily.observer.metrics.MetricsSource;
import com.lily.observer.metrics.TrafficMetrics;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 최근 1분 지표를 판정 규칙(RiskJudge)에 넣어 패널 상태를 만든다. 자동 판정 루프와 같은 기준이다 */
@Service
public class AppStatusService {

    private static final Duration CURRENT = Duration.ofMinutes(1);
    private static final Duration BASELINE_GAP = Duration.ofMinutes(5);
    private static final Duration BASELINE = Duration.ofMinutes(10);

    private final MetricsSource metrics;
    private final RiskJudge judge;
    private final Clock clock = Clock.systemUTC();

    public AppStatusService(MetricsSource metrics, RiskJudge judge) {
        this.metrics = metrics;
        this.judge = judge;
    }

    public AppStatus status(String namespace, String app) {
        Instant now = clock.instant();
        TrafficMetrics current = metrics.traffic(namespace, app, now, CURRENT);
        TrafficMetrics baseline = metrics.traffic(namespace, app, now.minus(BASELINE_GAP), BASELINE);
        Judgment judgment = judge.judge(app, current, baseline);
        return new AppStatus(app, namespace, judgment.level(), judgment.level().score(), judgment.level().color(),
                judgment.level().action(), message(judgment), judgment.reason(), current, baseline,
                judgment.judgedAt());
    }

    static String message(Judgment judgment) {
        TrafficMetrics m = judgment.target();
        return switch (judgment.level()) {
            case HOLD -> String.format("요청이 적어(분당 %.0f회) 판단을 보류해요", m.requestsPerMinute());
            case NORMAL -> "정상이에요";
            case NOTICE -> String.format("에러가 조금 늘었어요 (%.1f%%). 지켜보는 중이에요", m.errorRate() * 100);
            case WARNING -> String.format("주의: %s. 최근 배포와 에러 로그를 확인하세요", judgment.reason());
            case CRITICAL -> String.format("위험: 에러율 %.1f%%. 이전 버전으로 되돌리는 것을 권장해요",
                    m.errorRate() * 100);
        };
    }
}
