package com.lily.observer.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DiagnosisClient {
    private static final Set<String> CATEGORIES = Set.of("configuration", "database", "resources", "application", "traffic", "unknown");
    private static final Set<String> ACTIONS = Set.of("check_configuration", "check_database", "review_resources", "review_logs", "review_code", "review_rollback", "review_traffic");
    private final RestClient http;
    private final ObjectMapper json;
    private final DiagnosisProperties properties;

    @Autowired
    public DiagnosisClient(RestClient.Builder builder, ObjectMapper json, DiagnosisProperties properties) {
        this(client(builder), json, properties);
    }

    DiagnosisClient(RestClient http, ObjectMapper json, DiagnosisProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    private static RestClient client(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return builder.clone().requestFactory(factory).build();
    }

    public Diagnosis.Analysis analyze(Diagnosis.Request request) {
        try {
            URI base = URI.create(properties.builderUrl());
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalStateException("Invalid builder URL");
            }
            String url = base.toString().replaceAll("/+$", "") + "/api/diagnoses";
            var result = http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken()).body(request)
                    .exchange((out, in) -> {
                        if (!in.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Diagnosis service unavailable");
                        byte[] bytes = in.getBody().readNBytes(65_537);
                        if (bytes.length > 65_536) throw new IllegalStateException("Diagnosis response too large");
                        return json.readValue(bytes, Diagnosis.Analysis.class);
                    });
            validate(request, result);
            return new Diagnosis.Analysis(result.app(), result.namespace(), result.observedAt(), result.analyzedAt(),
                    result.source(), result.category(), DiagnosisMask.clean(result.summary(), 2000), result.evidenceIds(),
                    result.recommendations().stream().map(r -> new Diagnosis.Recommendation(r.action(),
                            DiagnosisMask.clean(r.reason(), 2000), r.evidenceIds())).toList(),
                    result.limitations().stream().map(s -> DiagnosisMask.clean(s, 2000)).toList());
        } catch (Exception ignored) {
            // Never propagate upstream bodies, tokens or exception messages into a public response.
            throw new IllegalStateException("Diagnosis service unavailable");
        }
    }

    private static void validate(Diagnosis.Request request, Diagnosis.Analysis result) {
        if (result == null || !request.app().equals(result.app()) || !request.namespace().equals(result.namespace())
                || !request.observedAt().equals(result.observedAt()) || result.analyzedAt() == null
                || !Set.of("ai", "rules").contains(result.source()) || !CATEGORIES.contains(result.category())
                || !text(result.summary()) || result.recommendations() == null || result.recommendations().size() > 7
                || result.limitations() == null || result.limitations().size() > 10
                || result.limitations().stream().anyMatch(s -> !text(s))) throw new IllegalArgumentException();
        Set<String> ids = request.evidence().stream().map(Diagnosis.Evidence::id).collect(Collectors.toSet());
        if (!references(result.evidenceIds(), ids)
                || (!"unknown".equals(result.category()) && result.evidenceIds().isEmpty())) throw new IllegalArgumentException();
        for (var r : result.recommendations()) {
            if (r == null || !ACTIONS.contains(r.action()) || !text(r.reason()) || !references(r.evidenceIds(), ids)
                    || r.evidenceIds().isEmpty()) throw new IllegalArgumentException();
        }
    }

    private static boolean references(List<String> values, Set<String> ids) {
        return values != null && values.size() <= 24 && values.stream().allMatch(v -> v != null && ids.contains(v))
                && Set.copyOf(values).size() == values.size();
    }

    private static boolean text(String value) { return value != null && !value.isBlank() && value.length() <= 2000; }
}
