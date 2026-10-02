package com.lily.observer.diagnosis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisMaskTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "password=hunter2", "{\"DB_PASSWORD\":\"hunter2\"}", "api-key: 'hunter2'",
            "Authorization: Bearer hunter2", "Basic hunter2", "postgres://user:hunter2@db/app",
            "token=hunter2", "Cookie: hunter2", "-----BEGIN PRIVATE KEY-----\nhunter2\n-----END PRIVATE KEY-----"
    })
    void masksCredentials(String input) {
        assertThat(DiagnosisMask.clean(input, 2000)).doesNotContain("hunter2").contains("[REDACTED]");
    }

    @Test
    void masksProviderCredentials() {
        String input = "AKIA1234567890123456 ghp_123abc github_pat_456def sk-providersecret eyJ123.abc.signature";
        assertThat(DiagnosisMask.clean(input, 2000)).isEqualTo("[REDACTED] [REDACTED] [REDACTED] [REDACTED] [REDACTED]");
    }

    @Test
    void masksBeforeTruncatingAndHandlesNull() {
        assertThat(DiagnosisMask.clean("password=" + "secretvalue".repeat(300), 16))
                .hasSize(16).doesNotContain("secret").endsWith("…");
        assertThat(DiagnosisMask.clean(null, 2000)).isEmpty();
    }
}
