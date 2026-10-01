package com.lily.observer.logs;

import java.time.Instant;
import java.util.List;

/**
 * 앱 컨테이너 로그를 읽는다. 기본 구현은 CloudWatch Logs (Fluent Bit 이 보낸 /lily/apps).
 */
public interface LogSource {

    /**
     * @param errorsOnly true 면 ERROR · Exception 이 들어간 줄만
     * @param limit      최근 것부터 최대 개수. 결과는 오래된 순
     */
    List<LogEntry> recent(String namespace, String app, Instant since, boolean errorsOnly, int limit);
}
