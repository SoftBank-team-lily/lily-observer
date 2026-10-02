package com.lily.observer.remediate;

import java.util.regex.Pattern;

/** 모델에 넘기기 전에 토큰과 비밀번호를 가린다. 로그 문장은 증거가 된다. */
public final class LogMask {

    private static final Pattern ASSIGNED = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|authorization)(\\s*[:=]\\s*)(\\S+)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern AWS = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
    private static final Pattern GITHUB = Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{10,}\\b");

    private LogMask() {
    }

    public static String mask(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String masked = ASSIGNED.matcher(line).replaceAll("$1$2***");
        masked = BEARER.matcher(masked).replaceAll("Bearer ***");
        masked = AWS.matcher(masked).replaceAll("***");
        masked = GITHUB.matcher(masked).replaceAll("***");
        return masked;
    }
}
