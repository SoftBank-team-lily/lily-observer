package com.lily.observer.diagnosis;

import java.util.regex.Pattern;

/** Mask before truncating: a cut through a credential must not leave a visible prefix. */
final class DiagnosisMask {
    private static final Pattern ASSIGNED = Pattern.compile(
            "(?i)([\\\"']?(?:[a-z0-9_.-]*[_-])?(?:password|passwd|secret|token|api[_-]?key|authorization|cookie|credential)[a-z0-9_.-]*[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[^\\s\\\"',;}]+");
    private static final Pattern URL_AUTH = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+:[^\\s/@]+@");
    private static final Pattern KEY = Pattern.compile("\\b(?:AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|sk-[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\\b");
    private static final Pattern PEM = Pattern.compile("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?(?:-----END [^-]*PRIVATE KEY-----|$)");

    static String clean(String value, int limit) {
        if (value == null) return "";
        String safe = PEM.matcher(value).replaceAll("[REDACTED]");
        safe = URL_AUTH.matcher(safe).replaceAll("$1[REDACTED]@");
        safe = AUTH.matcher(safe).replaceAll("[REDACTED]");
        safe = ASSIGNED.matcher(safe).replaceAll("$1[REDACTED]");
        safe = KEY.matcher(safe).replaceAll("[REDACTED]");
        return safe.length() <= limit ? safe : safe.substring(0, limit - 1) + "…";
    }
}
