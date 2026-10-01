package com.lily.observer.api;

import com.lily.observer.logs.LogEntry;
import com.lily.observer.logs.LogSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 대시보드 · AI 진단용 앱 로그 조회. 로그마다 파드 · 슬롯 · 이미지 버전이 붙어 어떤 배포에서 난 로그인지 보인다.
 */
@Validated
@RestController
@RequestMapping("/api/apps/{app}")
public class AppLogsController {

    private static final Duration MIN_SINCE = Duration.ofMinutes(1);
    private static final Duration MAX_SINCE = Duration.ofDays(7);   // CloudWatch 보관 기간

    private final LogSource logs;
    private final Clock clock = Clock.systemUTC();

    public AppLogsController(LogSource logs) {
        this.logs = logs;
    }

    /**
     * @param since 얼마 전부터 (예: 15m, 1h). 최대 7일
     * @param level error 면 ERROR · Exception 이 들어간 줄만, all 이면 전부
     * @param limit 최근 것부터 최대 개수. 결과는 오래된 순
     */
    @GetMapping("/logs")
    public List<LogEntry> logs(@PathVariable @Pattern(regexp = AppMetricsController.NAME) String app,
                               @RequestParam(defaultValue = "default") @Pattern(regexp = AppMetricsController.NAME)
                               String namespace,
                               @RequestParam(defaultValue = "15m") String since,
                               @RequestParam(defaultValue = "all") @Pattern(regexp = "all|error") String level,
                               @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        Duration range = Durations.parse("since", since, MIN_SINCE, MAX_SINCE);
        return logs.recent(namespace, app, clock.instant().minus(range), level.equals("error"), limit);
    }
}
