package com.lily.observer.remediate;

import com.lily.observer.logs.LogEntry;

import java.util.List;
import java.util.Map;

/** 기능이 꺼져 있으면 로그를 사건으로 만들지 않는다. */
public final class Remediate {

    private Remediate() {
    }

    public static Map<String, Object> consider(boolean enabled, String app, List<LogEntry> logs) {
        if (!enabled) {
            return Map.of("status", "off", "reason", "observer.remediate.enabled 가 꺼져 있다");
        }
        return IncidentDraft.from(app, logs)
                .<Map<String, Object>>map(incident -> Map.of(
                        "status", "ready",
                        "incident", incident))
                .orElseGet(() -> Map.of("status", "rejected", "reason", "레포 안 프레임이 없다"));
    }
}
