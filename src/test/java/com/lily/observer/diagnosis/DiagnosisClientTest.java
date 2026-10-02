package com.lily.observer.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static com.lily.observer.diagnosis.DiagnosisFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DiagnosisClientTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Diagnosis.Request request = request("default", "blog");
    private MockRestServiceServer server;
    private DiagnosisClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DiagnosisClient(builder.build(), json, enabled());
    }

    @Test
    void sendsBoundedEvidenceWithInternalBearerAndAcceptsRulesResult() throws Exception {
        server.expect(requestTo("http://builder.test/api/diagnoses"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer internal-token"))
                .andExpect(content().json(json.writeValueAsString(request)))
                .andRespond(withSuccess(json.writeValueAsString(analysis(request)), MediaType.APPLICATION_JSON));
        assertThat(client.analyze(request)).isEqualTo(analysis(request));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "namespace", "observedAt", "source", "category", "evidenceIds", "duplicateIds",
            "action", "recommendationIds", "missingIds", "blankSummary", "longSummary", "nullRecommendation", "nullLimitations"})
    void rejectsResponseOutsideTheEvidenceContract(String invalid) throws Exception {
        ObjectNode response = json.valueToTree(analysis(request));
        switch (invalid) {
            case "app" -> response.put("app", "another-app");
            case "namespace" -> response.put("namespace", "another-namespace");
            case "observedAt" -> response.put("observedAt", NOW.plusSeconds(1).toString());
            case "source" -> response.put("source", "trusted");
            case "category" -> response.put("category", "deploy_now");
            case "evidenceIds" -> response.putArray("evidenceIds").add("invented-id");
            case "duplicateIds" -> response.putArray("evidenceIds").add("pod-1").add("pod-1");
            case "action" -> ((ObjectNode) response.path("recommendations").get(0)).put("action", "execute_shell");
            case "recommendationIds" -> ((ObjectNode) response.path("recommendations").get(0)).putArray("evidenceIds").add("invented-id");
            case "missingIds" -> response.putArray("evidenceIds");
            case "blankSummary" -> response.put("summary", " ");
            case "longSummary" -> response.put("summary", "x".repeat(2001));
            case "nullRecommendation" -> response.putArray("recommendations").addNull();
            case "nullLimitations" -> response.putNull("limitations");
        }
        respond(response.toString());
        assertThatThrownBy(() -> client.analyze(request)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Diagnosis service unavailable");
        server.verify();
    }

    @Test
    void redactsModelOutputAgain() throws Exception {
        ObjectNode response = json.valueToTree(analysis(request));
        response.put("source", "ai").put("summary", "password=hunter2");
        ((ObjectNode) response.path("recommendations").get(0)).put("reason", "Bearer topsecret");
        response.putArray("limitations").add("postgres://user:dbsecret@db/app");
        respond(response.toString());
        var result = client.analyze(request);
        assertThat(result.toString()).doesNotContain("hunter2", "topsecret", "dbsecret").contains("[REDACTED]");
        server.verify();
    }

    @Test
    void upstreamErrorBodyAndOversizedBodyAreNotExposed() {
        server.expect(requestTo("http://builder.test/api/diagnoses"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("token=secret-provider-token"));
        assertThatThrownBy(() -> client.analyze(request)).hasMessage("Diagnosis service unavailable");
        server.verify();
        server.reset();
        respond("x".repeat(65_537));
        assertThatThrownBy(() -> client.analyze(request)).hasMessage("Diagnosis service unavailable");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "http://user:password@builder.test", "http://builder.test?token=secret", "http://builder.test#fragment"})
    void rejectsUnsafeConfiguredUrlsBeforeSending(String url) {
        var builder = RestClient.builder();
        var noRequests = MockRestServiceServer.bindTo(builder).build();
        var invalidClient = new DiagnosisClient(builder.build(), json, new DiagnosisProperties(true, url, "token"));
        assertThatThrownBy(() -> invalidClient.analyze(request)).hasMessage("Diagnosis service unavailable");
        noRequests.verify();
    }

    private void respond(String body) {
        server.expect(requestTo("http://builder.test/api/diagnoses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
