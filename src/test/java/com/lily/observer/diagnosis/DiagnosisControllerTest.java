package com.lily.observer.diagnosis;

import com.lily.observer.ObserverProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.lily.observer.diagnosis.DiagnosisFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DiagnosisController.class, properties = "observer.api-token=observer-token")
@EnableConfigurationProperties(ObserverProperties.class)
class DiagnosisControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DiagnosisService service;

    @Test
    void missingOrWrongBearerCannotTriggerDiagnosis() throws Exception {
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedRequestReturnsEvidenceAndDisablesHttpCaching() throws Exception {
        var request = request("default", "blog");
        when(service.diagnose("default", "blog")).thenReturn(new Diagnosis.Result("blog", "default", NOW,
                "ready", request.evidence(), request.missingSources(), analysis(request), "Advisory only"));
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis")
                        .header("Authorization", "Bearer observer-token"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.analysis.source").value("rules"))
                .andExpect(jsonPath("$.evidence[0].id").value("pod-1"));
        verify(service).diagnose("default", "blog");
    }

    @Test
    void usesExplicitNamespaceAndMapsUnavailableAndBusyStatuses() throws Exception {
        when(service.diagnose("tenant", "blog")).thenReturn(
                Diagnosis.Result.unavailable("blog", "tenant", "disabled", "Configure authentication"));
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis")
                        .param("namespace", "tenant").header("Authorization", "Bearer observer-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.state").value("disabled"));
        when(service.diagnose("tenant", "blog")).thenReturn(
                Diagnosis.Result.unavailable("blog", "tenant", "busy", "Try later"));
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis")
                        .param("namespace", "tenant").header("Authorization", "Bearer observer-token"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.state").value("busy"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Blog", "blog_1", "-blog", "blog-", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidAppNamesBeforeCollection(String app) throws Exception {
        String path = "/api/apps/" + app + "/diagnosis";
        mvc.perform(post(path).servletPath(path).header("Authorization", "Bearer observer-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidNamespaceBeforeCollection() throws Exception {
        mvc.perform(post("/api/apps/blog/diagnosis").servletPath("/api/apps/blog/diagnosis")
                        .param("namespace", "tenant_1").header("Authorization", "Bearer observer-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
