package com.lily.observer.remediate;

import com.lily.observer.logs.LogEntry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가린 로그에서 예외 서명과 레포 안 첫 프레임을 고른다.
 * 라이브러리 프레임만 있으면 코드 후보가 아니다.
 */
public final class IncidentDraft {

    private static final Pattern EXCEPTION = Pattern.compile("([A-Za-z_$][\\w$]*(?:Exception|Error))");
    private static final Pattern JAVA_FRAME = Pattern.compile("at ([\\w.$]+)\\(([^:)]+):(\\d+)\\)");
    private static final Pattern PYTHON_FRAME = Pattern.compile("File \"([^\"]+)\", line (\\d+)");
    private static final List<String> LIBRARIES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.springframework.", "org.apache.", "org.hibernate.",
            "io.netty.", "reactor.", "org.slf4j.", "ch.qos.", "kotlin.");

    private IncidentDraft() {
    }

    /** @param app 배포 앱 이름. 로그에는 없다 */
    public static Optional<Incident> from(String app, List<LogEntry> entries) {
        List<LogEntry> masked = entries.stream()
                .map(entry -> new LogEntry(entry.at(), entry.pod(), entry.slot(), entry.image(), LogMask.mask(entry.message())))
                .toList();
        List<String> blocks = StackJoiner.join(masked);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Optional<Incident> incident = read(app, blocks.get(i));
            if (incident.isPresent()) {
                return incident;
            }
        }
        return Optional.empty();
    }

    private static Optional<Incident> read(String app, String block) {
        Matcher exception = EXCEPTION.matcher(block);
        if (!exception.find()) {
            return Optional.empty();
        }
        String type = exception.group(1);
        Matcher java = JAVA_FRAME.matcher(block);
        while (java.find()) {
            String method = java.group(1);
            int dot = method.lastIndexOf('.');
            String typeName = dot < 0 ? method : method.substring(0, dot);
            String file = java.group(2);
            int line = Integer.parseInt(java.group(3));
            if (library(typeName) || !sourceFile(file)) {
                continue;
            }
            String path = javaPath(typeName, file);
            return Optional.of(incident(app, type, file, line, path, block));
        }
        Matcher python = PYTHON_FRAME.matcher(block);
        while (python.find()) {
            String path = python.group(1).replace('\\', '/');
            int line = Integer.parseInt(python.group(2));
            if (path.contains("site-packages/") || path.startsWith("/")) {
                continue;
            }
            String file = path.substring(path.lastIndexOf('/') + 1);
            return Optional.of(incident(app, type, file, line, path, block));
        }
        return Optional.empty();
    }

    private static Incident incident(String app, String type, String file, int line, String path, String block) {
        return new Incident(app, type + " " + file + ":" + line, block, List.of(path));
    }

    private static boolean library(String typeName) {
        for (String prefix : LIBRARIES) {
            if (typeName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceFile(String file) {
        return file.endsWith(".java") || file.endsWith(".kt") || file.endsWith(".kts");
    }

    /** {@code com.acme.OrderService} + {@code OrderService.java} → {@code src/main/java/com/acme/OrderService.java} */
    static String javaPath(String typeName, String file) {
        int dot = typeName.lastIndexOf('.');
        String pkg = dot < 0 ? "" : typeName.substring(0, dot).replace('.', '/');
        return pkg.isEmpty() ? file : "src/main/java/" + pkg + "/" + file;
    }

    /**
     * @param signature 예외 종류와 레포 안 첫 프레임. 같은 서명의 PR 은 하나만 연다
     * @param files     그 프레임의 파일 경로. 모델은 이 파일만 받는다
     */
    public record Incident(String app, String signature, String log, List<String> files) {
    }
}
