package com.lily.observer;

import com.lily.observer.cluster.ClusterSource;
import com.lily.observer.logs.LogSource;
import com.lily.observer.metrics.MetricsSource;
import com.lily.observer.remediate.RemediateWatch;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"observer.remediate.enabled=false", "observer.diagnosis.enabled=false"})
class ObserverApplicationTest {
    @MockitoBean KubernetesClient kubernetes;
    @MockitoBean ClusterSource cluster;
    @MockitoBean MetricsSource metrics;
    @MockitoBean LogSource logs;
    @Autowired ApplicationContext context;

    @Test void startsWithProductionConstructorWiring() {
        assertThat(context.getBean(RemediateWatch.class)).isNotNull();
    }
}
