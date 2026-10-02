package com.lily.observer.diagnosis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** Internal, advisory diagnosis contract. No action in this response is executed. */
public final class Diagnosis {
    private Diagnosis() {}

    public record Evidence(String id, String source, String signal, String summary) {}
    public record Request(String app, String namespace, Instant observedAt,
                          List<Evidence> evidence, List<String> missingSources) {}
    public record Recommendation(String action, String reason, List<String> evidenceIds) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analysis(String app, String namespace, Instant observedAt, Instant analyzedAt,
                           String source, String category, String summary, List<String> evidenceIds,
                           List<Recommendation> recommendations, List<String> limitations) {}
    public record Result(String app, String namespace, Instant observedAt, String state,
                         List<Evidence> evidence, List<String> missingSources, Analysis analysis,
                         String message) {
        static Result unavailable(String app, String namespace, String state, String message) {
            return new Result(app, namespace, null, state, List.of(), List.of(), null, message);
        }
    }
}
