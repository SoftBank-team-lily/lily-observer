package com.lily.observer.diagnosis;

import com.lily.observer.ObserverProperties;

import java.time.Instant;
import java.util.List;

final class DiagnosisFixtures {
    static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    static ObserverProperties observer(String token) {
        return new ObserverProperties(token, null, null,
                new ObserverProperties.Judge(20, .01, .02, .05, 2, 2), null, null, null);
    }

    static DiagnosisProperties enabled() {
        return new DiagnosisProperties(true, "http://builder.test", "internal-token");
    }

    static Diagnosis.Request request(String namespace, String app) {
        return new Diagnosis.Request(app, namespace, NOW,
                List.of(new Diagnosis.Evidence("pod-1", "pods", "oom", "lastRestartReason=OOMKilled")), List.of("logs"));
    }

    static Diagnosis.Analysis analysis(Diagnosis.Request request) {
        return new Diagnosis.Analysis(request.app(), request.namespace(), request.observedAt(), NOW.plusSeconds(1),
                "rules", "resources", "Memory exhaustion observed", List.of("pod-1"),
                List.of(new Diagnosis.Recommendation("review_resources", "Review memory limits", List.of("pod-1"))),
                List.of("Logs were unavailable"));
    }
}
