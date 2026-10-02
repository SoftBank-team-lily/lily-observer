package com.lily.observer.remediate;

import com.lily.observer.logs.LogEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent Bit 이 줄마다 나눠 저장한 스택을 같은 파드·같은 시각으로 다시 잇는다.
 * 이어 붙는 줄은 {@code at}, {@code Caused by}, {@code Suppressed}, {@code ...} 로 시작한다.
 */
public final class StackJoiner {

    private StackJoiner() {
    }

    public static List<String> join(List<LogEntry> entries) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        String pod = null;
        Instant at = null;
        for (LogEntry entry : entries) {
            String message = entry.message() == null ? "" : entry.message();
            boolean attach = current != null
                    && entry.pod() != null
                    && entry.pod().equals(pod)
                    && entry.at() != null
                    && entry.at().equals(at)
                    && continues(message);
            if (!attach) {
                if (current != null) {
                    blocks.add(current.toString());
                }
                current = new StringBuilder(message);
                pod = entry.pod();
                at = entry.at();
            } else {
                current.append('\n').append(message);
            }
        }
        if (current != null) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    static boolean continues(String message) {
        String trimmed = message.stripLeading();
        return trimmed.startsWith("at ")
                || trimmed.startsWith("Caused by:")
                || trimmed.startsWith("Suppressed:")
                || trimmed.startsWith("...");
    }
}
