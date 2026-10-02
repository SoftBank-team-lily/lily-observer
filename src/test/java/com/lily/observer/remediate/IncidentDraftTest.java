package com.lily.observer.remediate;

import com.lily.observer.logs.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentDraftTest {

    private static final Instant AT = Instant.parse("2026-10-01T06:22:49.953Z");

    @Test
    void joinsStackLinesAndMasksSecrets() {
        List<LogEntry> lines = List.of(
                line("java.lang.IllegalStateException: token=ghp_abcdefghij1234567890"),
                line("\tat com.acme.OrderService.label(OrderService.java:42)"),
                line("\tat org.springframework.web.Foo.bar(Foo.java:10)"));

        IncidentDraft.Incident incident = IncidentDraft.from("blog", lines).orElseThrow();

        assertThat(incident.signature()).isEqualTo("IllegalStateException OrderService.java:42");
        assertThat(incident.files()).containsExactly("src/main/java/com/acme/OrderService.java");
        assertThat(incident.log()).contains("OrderService.java:42").contains("token=***").doesNotContain("ghp_");
    }

    @Test
    void libraryOnlyFramesAreNotACodeCandidate() {
        List<LogEntry> lines = List.of(
                line("java.lang.NullPointerException"),
                line("\tat org.springframework.web.Foo.bar(Foo.java:10)"));

        assertThat(IncidentDraft.from("blog", lines)).isEmpty();
    }

    @Test
    void staysOffWithoutReadingTheLog() {
        LogEntry secret = line("password=hunter2");

        MapResult off = new MapResult(Remediate.consider(false, "blog", List.of(secret)));

        assertThat(off.status).isEqualTo("off");
        assertThat(off.reason).contains("꺼져");
    }

    private static LogEntry line(String message) {
        return new LogEntry(AT, "blog-green-1", "green", "blog:1", message);
    }

    private record MapResult(String status, String reason) {
        MapResult(java.util.Map<String, Object> body) {
            this(String.valueOf(body.get("status")), String.valueOf(body.get("reason")));
        }
    }
}
