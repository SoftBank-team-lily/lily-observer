package com.lily.observer.api;

import com.lily.observer.metrics.MetricsSource;
import com.lily.observer.metrics.TrafficMetrics;
import com.lily.observer.metrics.TrafficPoint;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 대시보드 · AI 진단용 앱 지표 조회. ingress-nginx 입구 기준이라 앱 언어와 상관없다.
 */
@Validated
@RestController
@RequestMapping("/api/apps/{app}")
public class AppMetricsController {

    static final String NAME = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private static final Duration CURRENT = Duration.ofMinutes(1);
    private static final Duration STEP = Duration.ofSeconds(30);
    private static final Duration MAX_WINDOW = Duration.ofHours(6);

    private final MetricsSource metrics;
    private final Clock clock = Clock.systemUTC();

    public AppMetricsController(MetricsSource metrics) {
        this.metrics = metrics;
    }

    /**
     * @param window 추이 구간 (예: 10m, 1h). 1분 ~ 6시간
     */
    @GetMapping("/metrics")
    public AppMetrics metrics(@PathVariable @Pattern(regexp = NAME) String app,
                              @RequestParam(defaultValue = "default") @Pattern(regexp = NAME) String namespace,
                              @RequestParam(defaultValue = "10m") String window) {
        Duration range = Durations.parse("window", window, CURRENT, MAX_WINDOW);
        Instant now = clock.instant();
        return new AppMetrics(app, namespace,
                metrics.traffic(namespace, app, now, CURRENT),
                metrics.series(namespace, app, now.minus(range), now, STEP));
    }

    /**
     * @param current 최근 1분 (카드)
     * @param series  30초 간격 추이 (그래프)
     */
    public record AppMetrics(String app, String namespace, TrafficMetrics current, List<TrafficPoint> series) {}
}
